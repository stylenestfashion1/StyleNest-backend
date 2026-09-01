package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.product.ProductVariantRequest;
import com.stylenest.stylenest_backend.dto.product.ProductVariantResponse;

public interface ProductVariantService {

    ProductVariantResponse createVariant(Long productId, ProductVariantRequest request);

    List<ProductVariantResponse> getVariantsByProduct(Long productId);

    ProductVariantResponse updateVariant(Long variantId, ProductVariantRequest request);

    void deleteVariant(Long variantId);

}