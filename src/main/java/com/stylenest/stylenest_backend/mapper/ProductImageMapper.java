package com.stylenest.stylenest_backend.mapper;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.product.ProductImageRequest;
import com.stylenest.stylenest_backend.dto.product.ProductImageResponse;
import com.stylenest.stylenest_backend.entity.ProductImage;

@Component
public class ProductImageMapper {

    public ProductImage toEntity(ProductImageRequest request) {
        return ProductImage.builder()
                .imageUrl(request.getImageUrl())
                .displayOrder(request.getDisplayOrder())
                .build();
    }

    public ProductImageResponse toResponse(ProductImage image) {
        return ProductImageResponse.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .build();
    }

    public void updateEntity(ProductImage image, ProductImageRequest request) {
        image.setImageUrl(request.getImageUrl());
        image.setDisplayOrder(request.getDisplayOrder());
    }
}