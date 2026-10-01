package com.stylenest.stylenest_backend.service.impl;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.rental.RentalActiveCatalogResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogItemRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogItemResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogPublicResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogSummaryResponse;
import com.stylenest.stylenest_backend.entity.RentalCatalog;
import com.stylenest.stylenest_backend.entity.RentalCatalogItem;
import com.stylenest.stylenest_backend.entity.RentalCatalogItemImage;
import com.stylenest.stylenest_backend.enums.RentalCatalogStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.RentalCatalogMapper;
import com.stylenest.stylenest_backend.repository.RentalCatalogItemRepository;
import com.stylenest.stylenest_backend.repository.RentalCatalogRepository;
import com.stylenest.stylenest_backend.service.RentalCatalogService;
import com.stylenest.stylenest_backend.service.RentalImageStorageService;

import lombok.RequiredArgsConstructor;

/**
 * Fully isolated from every retail service -- no dependency on
 * ProductRepository/OrderRepository/etc. anywhere in this class. Deleting a
 * catalog or an item here only ever touches rental_catalogs/
 * rental_catalog_items/rental_catalog_item_images rows and files under
 * uploads/rental/, via RentalImageStorageService (never the product
 * ImageStorageService).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RentalCatalogServiceImpl implements RentalCatalogService {

    private static final String TOKEN_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LENGTH = 24;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RentalCatalogRepository rentalCatalogRepository;
    private final RentalCatalogItemRepository rentalCatalogItemRepository;
    private final RentalCatalogMapper rentalCatalogMapper;
    private final RentalImageStorageService rentalImageStorageService;

    @Override
    public RentalCatalogResponse createCatalog(RentalCatalogRequest request) {

        RentalCatalog catalog = RentalCatalog.builder()
                .name(request.getName())
                .shareToken(generateUniqueShareToken())
                .status(RentalCatalogStatus.ACTIVE)
                .build();

        catalog = rentalCatalogRepository.save(catalog);

        return rentalCatalogMapper.toResponse(catalog);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RentalCatalogSummaryResponse> getAllCatalogs() {

        return rentalCatalogRepository.findAll()
                .stream()
                .map(rentalCatalogMapper::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RentalCatalogResponse getCatalogById(Long id) {

        return rentalCatalogMapper.toResponse(findCatalog(id));
    }

    @Override
    public RentalCatalogResponse renameCatalog(Long id, RentalCatalogRequest request) {

        RentalCatalog catalog = findCatalog(id);

        catalog.setName(request.getName());

        return rentalCatalogMapper.toResponse(rentalCatalogRepository.save(catalog));
    }

    @Override
    public RentalCatalogResponse activateCatalog(Long id) {

        RentalCatalog catalog = findCatalog(id);

        catalog.setStatus(RentalCatalogStatus.ACTIVE);

        return rentalCatalogMapper.toResponse(rentalCatalogRepository.save(catalog));
    }

    @Override
    public RentalCatalogResponse deactivateCatalog(Long id) {

        RentalCatalog catalog = findCatalog(id);

        catalog.setStatus(RentalCatalogStatus.INACTIVE);

        return rentalCatalogMapper.toResponse(rentalCatalogRepository.save(catalog));
    }

    @Override
    public void deleteCatalog(Long id) {

        RentalCatalog catalog = findCatalog(id);

        // Collect every image URL before the cascade delete removes the
        // rows -- files are removed only after the DB delete succeeds, so a
        // failed transaction never leaves the DB and filesystem out of sync
        // with each other in the "DB still references a now-deleted file"
        // direction.
        List<String> imageUrls = catalog.getItems().stream()
                .flatMap(item -> item.getImages().stream())
                .map(RentalCatalogItemImage::getImageUrl)
                .toList();

        rentalCatalogRepository.delete(catalog);

        imageUrls.forEach(rentalImageStorageService::deleteIfManaged);
    }

    @Override
    public RentalCatalogItemResponse addItem(Long catalogId, RentalCatalogItemRequest request) {

        RentalCatalog catalog = findCatalog(catalogId);

        int nextDisplayOrder = catalog.getItems().size();

        RentalCatalogItem item = RentalCatalogItem.builder()
                .catalog(catalog)
                .name(request.getName())
                .colour(request.getColour())
                .rentalPrice(request.getRentalPrice())
                .displayOrder(nextDisplayOrder)
                .build();

        attachImages(item, request.getImageUrls());

        catalog.getItems().add(item);

        rentalCatalogRepository.save(catalog);

        return rentalCatalogMapper.toItemResponse(item);
    }

    @Override
    public RentalCatalogItemResponse updateItem(Long catalogId, Long itemId, RentalCatalogItemRequest request) {

        RentalCatalog catalog = findCatalog(catalogId);
        RentalCatalogItem item = findItemInCatalog(catalog, itemId);

        // Images being replaced: delete the files no longer referenced by
        // the new list so the VPS doesn't silently accumulate orphaned
        // photos every time an admin swaps a lehenga's images.
        List<String> previousUrls = item.getImages().stream()
                .map(RentalCatalogItemImage::getImageUrl)
                .toList();

        item.setName(request.getName());
        item.setColour(request.getColour());
        item.setRentalPrice(request.getRentalPrice());

        item.getImages().clear();
        attachImages(item, request.getImageUrls());

        rentalCatalogRepository.save(catalog);

        List<String> newUrls = request.getImageUrls() == null ? List.of() : request.getImageUrls();
        previousUrls.stream()
                .filter(url -> !newUrls.contains(url))
                .forEach(rentalImageStorageService::deleteIfManaged);

        return rentalCatalogMapper.toItemResponse(item);
    }

    @Override
    public void deleteItem(Long catalogId, Long itemId) {

        RentalCatalog catalog = findCatalog(catalogId);
        RentalCatalogItem item = findItemInCatalog(catalog, itemId);

        List<String> imageUrls = item.getImages().stream()
                .map(RentalCatalogItemImage::getImageUrl)
                .toList();

        catalog.getItems().remove(item);

        rentalCatalogRepository.save(catalog);

        imageUrls.forEach(rentalImageStorageService::deleteIfManaged);
    }

    @Override
    public RentalCatalogResponse reorderItems(Long catalogId, List<Long> orderedItemIds) {

        RentalCatalog catalog = findCatalog(catalogId);

        Map<Long, RentalCatalogItem> byId = new HashMap<>();
        catalog.getItems().forEach(item -> byId.put(item.getId(), item));

        if (orderedItemIds.size() != byId.size() || !byId.keySet().containsAll(orderedItemIds)) {
            throw new BadRequestException("orderedItemIds must contain exactly this catalog's item ids.");
        }

        for (int i = 0; i < orderedItemIds.size(); i++) {
            byId.get(orderedItemIds.get(i)).setDisplayOrder(i);
        }

        return rentalCatalogMapper.toResponse(rentalCatalogRepository.save(catalog));
    }

    @Override
    @Transactional(readOnly = true)
    public RentalCatalogPublicResponse getPublicCatalogByShareToken(String shareToken) {

        RentalCatalog catalog = rentalCatalogRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Rental catalog not found."));

        // Same generic "not found" whether the token is simply wrong or the
        // catalog exists but is INACTIVE -- a customer's browser never
        // learns which, matching the admin's "deactivate" expectation that
        // the link goes quiet rather than revealing catalog state.
        if (catalog.getStatus() != RentalCatalogStatus.ACTIVE) {
            throw new ResourceNotFoundException("Rental catalog not found.");
        }

        return rentalCatalogMapper.toPublicResponse(catalog);
    }

    @Override
    @Transactional(readOnly = true)
    public RentalActiveCatalogResponse getActiveCatalog() {

        return rentalCatalogRepository.findFirstByStatusOrderByCreatedAtDesc(RentalCatalogStatus.ACTIVE)
                .map(catalog -> RentalActiveCatalogResponse.builder()
                        .available(true)
                        .shareToken(catalog.getShareToken())
                        .build())
                .orElseGet(() -> RentalActiveCatalogResponse.builder()
                        .available(false)
                        .build());
    }

    private void attachImages(RentalCatalogItem item, List<String> imageUrls) {

        if (imageUrls == null) {
            return;
        }

        for (int i = 0; i < imageUrls.size(); i++) {
            item.getImages().add(RentalCatalogItemImage.builder()
                    .item(item)
                    .imageUrl(imageUrls.get(i))
                    .displayOrder(i)
                    .build());
        }
    }

    private RentalCatalog findCatalog(Long id) {

        return rentalCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rental catalog not found with id: " + id));
    }

    private RentalCatalogItem findItemInCatalog(RentalCatalog catalog, Long itemId) {

        return catalog.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rental item not found with id: " + itemId + " in this catalog."));
    }

    private String generateUniqueShareToken() {

        String token;

        do {
            token = randomToken();
        } while (rentalCatalogRepository.existsByShareToken(token));

        return token;
    }

    private String randomToken() {

        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);

        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(TOKEN_CHARS.charAt(SECURE_RANDOM.nextInt(TOKEN_CHARS.length())));
        }

        return sb.toString();
    }
}
