package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.product.ProductImageRequest;
import com.stylenest.stylenest_backend.dto.product.ProductImageResponse;

public interface ProductImageService {

    ProductImageResponse addImage(Long variantId, ProductImageRequest request);

    List<ProductImageResponse> getImagesByVariant(Long variantId);

    void deleteImage(Long imageId);

}