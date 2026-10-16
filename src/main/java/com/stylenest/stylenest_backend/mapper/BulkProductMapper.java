package com.stylenest.stylenest_backend.mapper;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.bulk.BulkProductResponse;
import com.stylenest.stylenest_backend.entity.BulkProduct;

@Component
public class BulkProductMapper {

    public BulkProductResponse toResponse(BulkProduct product) {

        java.util.List<String> images = product.getImageUrls() != null && !product.getImageUrls().isEmpty()
                ? new java.util.ArrayList<>(product.getImageUrls())
                : (product.getImageUrl() != null ? java.util.List.of(product.getImageUrl()) : java.util.List.of());

        return BulkProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .imageUrls(images)
                .price(product.getPrice())
                .minOrderQuantity(product.getMinOrderQuantity())
                .availableStock(product.getAvailableStock())
                .hsnCode(product.getHsnCode())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .categoryGender(product.getCategory().getGender())
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
