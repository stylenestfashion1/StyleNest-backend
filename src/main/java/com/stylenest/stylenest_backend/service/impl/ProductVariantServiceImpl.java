package com.stylenest.stylenest_backend.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.product.ProductImageResponse;
import com.stylenest.stylenest_backend.dto.product.ProductVariantRequest;
import com.stylenest.stylenest_backend.dto.product.ProductVariantResponse;
import com.stylenest.stylenest_backend.dto.product.RenameColorGroupRequest;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductImage;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.enums.Size;
import com.stylenest.stylenest_backend.exception.DuplicateResourceException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.exception.VariantAlreadyExistsException;
import com.stylenest.stylenest_backend.mapper.ProductImageMapper;
import com.stylenest.stylenest_backend.mapper.ProductVariantMapper;
import com.stylenest.stylenest_backend.repository.ProductImageRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.service.ProductVariantService;
import com.stylenest.stylenest_backend.util.ColorNormalizer;
import com.stylenest.stylenest_backend.util.SkuGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final ProductVariantMapper variantMapper;
    private final ProductImageMapper imageMapper;

    @Override
    public ProductVariantResponse createVariant(Long productId, ProductVariantRequest request) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + productId));

        if (request.getStock() < 0) {
            throw new IllegalArgumentException("Stock cannot be negative.");
        }

        request.setColor(ColorNormalizer.normalize(request.getColor()));

        if (variantRepository.existsByProductAndColorAndSize(
                product,
                request.getColor(),
                request.getSize())) {

            throw new VariantAlreadyExistsException(
                    "Variant already exists for this product.");
        }

        ProductVariant variant = variantMapper.toEntity(request);
        variant.setProduct(product);
        variant.setSku(generateVariantSku(product, variant.getColor(), variant.getSize()));

        ProductVariant savedVariant = variantRepository.save(variant);

        return variantMapper.toResponse(savedVariant, imagesFor(productId, savedVariant.getColor()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getVariantsByProduct(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + productId));

        List<ProductVariant> variants = variantRepository.findByProduct(product);

        // One query for every color this product has, instead of one
        // query per variant -- every size of the same color shares this
        // same image set.
        Map<String, List<ProductImageResponse>> imagesByColor = new HashMap<>();
        for (ProductImage image : imageRepository.findByProductIdOrderByColorAscDisplayOrderAsc(productId)) {
            imagesByColor
                    .computeIfAbsent(image.getColor(), c -> new java.util.ArrayList<>())
                    .add(imageMapper.toResponse(image));
        }

        return variants.stream()
                .map(variant -> variantMapper.toResponse(
                        variant, imagesByColor.getOrDefault(variant.getColor(), List.of())))
                .toList();
    }

    @Override
    public ProductVariantResponse updateVariant(Long variantId,
                                                ProductVariantRequest request) {

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Variant not found with id: " + variantId));

        if (request.getStock() < 0) {
            throw new IllegalArgumentException("Stock cannot be negative.");
        }

        request.setColor(ColorNormalizer.normalize(request.getColor()));

        if ((!variant.getColor().equals(request.getColor())
                || !variant.getSize().equals(request.getSize()))
                && variantRepository.existsByProductAndColorAndSize(
                        variant.getProduct(),
                        request.getColor(),
                        request.getSize())) {

            throw new VariantAlreadyExistsException(
                    "Variant already exists for this product.");
        }

        variantMapper.updateEntity(variant, request);

        ProductVariant updatedVariant = variantRepository.save(variant);

        return variantMapper.toResponse(
                updatedVariant, imagesFor(updatedVariant.getProduct().getId(), updatedVariant.getColor()));
    }

    @Override
    public void deleteVariant(Long variantId) {

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Variant not found with id: " + variantId));

        variantRepository.delete(variant);
    }

    @Override
    public List<ProductVariantResponse> renameColorGroup(
            Long productId, String currentColor, RenameColorGroupRequest request) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + productId));

        String normalizedCurrent = ColorNormalizer.normalize(currentColor);
        String normalizedNew = ColorNormalizer.normalize(request.getNewColor());

        List<ProductVariant> group = variantRepository.findByProductAndColor(product, normalizedCurrent);

        if (group.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No variants found for color \"" + currentColor + "\" on this product.");
        }

        // Renaming to the same color (only case changes, or just re-saving colorHex) is a no-op
        // rename, not a collision with "itself".
        boolean isActualRename = !normalizedNew.equals(normalizedCurrent);

        if (isActualRename && variantRepository.existsByProductAndColor(product, normalizedNew)) {
            throw new DuplicateResourceException(
                    "\"" + request.getNewColor() + "\" already exists for this product.");
        }

        // One shared color code for the whole group -- every size gets the same new code, kept
        // distinct from every OTHER color currently on this product (the group being renamed is
        // excluded from that comparison set since its own rows are the ones changing).
        String newColorCode = isActualRename
                ? generateColorCodeForRename(product, normalizedNew, normalizedCurrent)
                : null;

        String prefix = product.getSku() != null ? product.getSku() : "PRD";

        Set<String> skusAssignedThisBatch = new HashSet<>();
        for (ProductVariant variant : group) {
            variant.setColor(normalizedNew);
            if (request.getNewColorHex() != null) {
                variant.setColorHex(request.getNewColorHex());
            }
            if (isActualRename) {
                variant.setSku(uniqueSkuFor(prefix, newColorCode, variant.getSize(), skusAssignedThisBatch));
            }
        }
        variantRepository.saveAll(group);

        // Images belong to (product, color) -- they already exclusively belong to this group
        // (see ProductImageRepository), so renaming just relabels them to follow their own
        // variants. This never reassigns a DIFFERENT color's images: the query below only ever
        // returns images that were already this color's.
        if (isActualRename) {
            List<ProductImage> images =
                    imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(productId, normalizedCurrent);
            for (ProductImage image : images) {
                image.setColor(normalizedNew);
            }
            imageRepository.saveAll(images);
        }

        List<ProductImageResponse> images = imagesFor(productId, normalizedNew);
        return group.stream()
                .map(variant -> variantMapper.toResponse(variant, images))
                .toList();
    }

    /**
     * One fresh, collision-resistant color code for a rename target, considering every OTHER
     * color currently on this product (not the group being renamed, whose old code is about to
     * stop existing) plus a live existsBySku check per candidate size -- the same collision
     * safety net as generateVariantSku, since a rename can just as easily land on an SKU some
     * other color already owns.
     */
    private String generateColorCodeForRename(Product product, String newColor, String excludeColor) {

        Map<String, String> otherColorCodesInUse = new HashMap<>();
        for (ProductVariant existing : variantRepository.findByProduct(product)) {
            if (existing.getSku() == null || existing.getColor().equals(excludeColor)) {
                continue;
            }
            String[] parts = existing.getSku().split("-");
            if (parts.length == 4) {
                otherColorCodesInUse.putIfAbsent(existing.getColor(), parts[2]);
            }
        }

        return SkuGenerator.colorCode(newColor, new HashSet<>(otherColorCodesInUse.values()));
    }

    /**
     * Builds "STN-{prefix}-{colorCode}-{sizeLabel}", verified unique against the database and
     * against every SKU already assigned earlier in the same batch (mutated as a side effect).
     * Falls back to alternate color codes on collision rather than ever risking a duplicate-key
     * insert -- the same guarantee generateVariantSku makes for a single new variant.
     */
    private String uniqueSkuFor(String prefix, String colorCode, Size size, Set<String> skusAssignedThisBatch) {

        String sku = "STN-" + prefix + "-" + colorCode + "-" + size.getSkuToken();

        for (int attempt = 1; attempt < 1000; attempt++) {
            if (!skusAssignedThisBatch.contains(sku) && !variantRepository.existsBySku(sku)) {
                skusAssignedThisBatch.add(sku);
                return sku;
            }
            String altCode = SkuGenerator.colorCode(colorCode + attempt, skusAssignedThisBatch);
            sku = "STN-" + prefix + "-" + altCode + "-" + size.getSkuToken();
        }

        throw new IllegalStateException("Could not generate a unique SKU for prefix " + prefix);
    }

    private List<ProductImageResponse> imagesFor(Long productId, String color) {
        return imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(productId, color)
                .stream()
                .map(imageMapper::toResponse)
                .toList();
    }

    /**
     * Builds this variant's SKU as STN-{productPrefix}-{colorCode}-{sizeLabel}. Every size of an
     * already-used color on this product reuses that color's existing code (parsed from a
     * sibling variant's SKU) so e.g. BLUE/S and BLUE/M always share "BLU" -- only a color new to
     * this product goes through SkuGenerator to mint a fresh, collision-resistant code.
     *
     * <p>A stray bad historical SKU (e.g. a row whose color code doesn't match its siblings --
     * this happened once, see the 2026-09 incident where a BLUE variant had picked up FADED
     * BLUE's code and a later BLUE+XXL insert collided with FADED BLUE's real SKU, failing with
     * an unhandled constraint violation) must never crash a create again. The final {@code
     * existsBySku} check below is the actual guarantee: whatever code got reused or generated,
     * this only returns once the full SKU is verified unique, minting a fresh alternate on
     * collision instead of trusting sibling data blindly.
     */
    private String generateVariantSku(Product product, String color, Size size) {

        Map<String, String> colorCodesInUse = new HashMap<>();
        for (ProductVariant existing : variantRepository.findByProduct(product)) {
            if (existing.getSku() == null) {
                continue;
            }
            String[] parts = existing.getSku().split("-");
            if (parts.length == 4) {
                colorCodesInUse.putIfAbsent(existing.getColor(), parts[2]);
            }
        }

        String colorCode = colorCodesInUse.get(color);
        if (colorCode == null) {
            colorCode = SkuGenerator.colorCode(color, new HashSet<>(colorCodesInUse.values()));
        }

        String prefix = product.getSku() != null ? product.getSku() : "PRD";

        String sku = "STN-" + prefix + "-" + colorCode + "-" + size.getSkuToken();

        if (!variantRepository.existsBySku(sku)) {
            return sku;
        }

        // The natural SKU collided with something else already in the table (bad historical
        // data, or two colors that happen to mint the same code) -- fall back to a fresh,
        // guaranteed-unique color code for this specific attempt rather than fail the whole
        // create. Rare in practice; correctness matters more than a perfectly clean code here.
        Set<String> allCodesInUse = new HashSet<>(colorCodesInUse.values());
        for (int attempt = 2; attempt < 1000; attempt++) {
            String candidateCode = SkuGenerator.colorCode(color + attempt, allCodesInUse);
            String candidateSku = "STN-" + prefix + "-" + candidateCode + "-" + size.getSkuToken();
            if (!variantRepository.existsBySku(candidateSku)) {
                return candidateSku;
            }
            allCodesInUse.add(candidateCode);
        }

        throw new IllegalStateException("Could not generate a unique SKU for product " + product.getId());
    }
}
