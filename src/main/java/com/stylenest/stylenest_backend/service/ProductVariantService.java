package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.product.ProductVariantRequest;
import com.stylenest.stylenest_backend.dto.product.ProductVariantResponse;
import com.stylenest.stylenest_backend.dto.product.RenameColorGroupRequest;

public interface ProductVariantService {

    ProductVariantResponse createVariant(Long productId, ProductVariantRequest request);

    List<ProductVariantResponse> getVariantsByProduct(Long productId);

    ProductVariantResponse updateVariant(Long variantId, ProductVariantRequest request);

    void deleteVariant(Long variantId);

    /**
     * Renames an entire color group in one operation: every existing size that currently has
     * {@code currentColor} becomes {@code request.getNewColor()}, preserving each variant's id,
     * stock, and order history. Does not create or remove any size. Images already associated
     * with this color follow the rename (they are not reassigned from a different color).
     */
    List<ProductVariantResponse> renameColorGroup(Long productId, String currentColor, RenameColorGroupRequest request);

}