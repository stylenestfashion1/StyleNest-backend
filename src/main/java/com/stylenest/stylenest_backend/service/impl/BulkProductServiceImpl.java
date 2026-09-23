package com.stylenest.stylenest_backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.bulk.BulkProductRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkProductResponse;
import com.stylenest.stylenest_backend.entity.BulkCategory;
import com.stylenest.stylenest_backend.entity.BulkProduct;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.BulkProductMapper;
import com.stylenest.stylenest_backend.repository.BulkCategoryRepository;
import com.stylenest.stylenest_backend.repository.BulkOrderItemRepository;
import com.stylenest.stylenest_backend.repository.BulkProductRepository;
import com.stylenest.stylenest_backend.service.BulkProductService;
import com.stylenest.stylenest_backend.util.SlugUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BulkProductServiceImpl implements BulkProductService {

    private final BulkProductRepository bulkProductRepository;
    private final BulkCategoryRepository bulkCategoryRepository;
    private final BulkOrderItemRepository bulkOrderItemRepository;
    private final BulkProductMapper bulkProductMapper;

    @Override
    public BulkProductResponse createProduct(BulkProductRequest request) {

        BulkCategory category = findCategory(request.getCategoryId());

        BulkProduct product = BulkProduct.builder()
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .price(request.getPrice())
                .minOrderQuantity(request.getMinOrderQuantity())
                .availableStock(request.getAvailableStock())
                .hsnCode(request.getHsnCode())
                .category(category)
                .active(request.getActive())
                .slug(SlugUtil.generateSlug(request.getName()) + "-" + System.currentTimeMillis())
                .build();

        product = bulkProductRepository.save(product);

        return bulkProductMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BulkProductResponse> getAllProducts() {

        return bulkProductRepository.findAll()
                .stream()
                .map(bulkProductMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BulkProductResponse> getActiveProducts(Long categoryId) {

        List<BulkProduct> products = categoryId != null
                ? bulkProductRepository.findByActiveTrueAndCategoryId(categoryId)
                : bulkProductRepository.findByActiveTrue();

        return products.stream()
                .map(bulkProductMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BulkProductResponse getProductById(Long id) {

        return bulkProductMapper.toResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public BulkProductResponse getActiveProductById(Long id) {

        BulkProduct product = findById(id);

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new ResourceNotFoundException("Product not found.");
        }

        return bulkProductMapper.toResponse(product);
    }

    @Override
    public BulkProductResponse updateProduct(Long id, BulkProductRequest request) {

        BulkProduct product = findById(id);

        BulkCategory category = findCategory(request.getCategoryId());

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setImageUrl(request.getImageUrl());
        product.setPrice(request.getPrice());
        product.setMinOrderQuantity(request.getMinOrderQuantity());
        product.setAvailableStock(request.getAvailableStock());
        product.setHsnCode(request.getHsnCode());
        product.setCategory(category);
        product.setActive(request.getActive());

        product = bulkProductRepository.save(product);

        return bulkProductMapper.toResponse(product);
    }

    @Override
    public void deleteProduct(Long id) {

        BulkProduct product = findById(id);

        if (bulkOrderItemRepository.existsByBulkProduct(product)) {
            throw new BadRequestException(
                    "This bulk product has existing order history and cannot be deleted. "
                            + "Deactivate it instead.");
        }

        bulkProductRepository.delete(product);
    }

    private BulkProduct findById(Long id) {

        return bulkProductRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bulk product not found with id: " + id));
    }

    private BulkCategory findCategory(Long categoryId) {

        return bulkCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Bulk category not found with id: " + categoryId));
    }
}
