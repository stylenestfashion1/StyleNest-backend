package com.stylenest.stylenest_backend.mapper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.dto.bulk.BulkColorOption;
import com.stylenest.stylenest_backend.dto.bulk.BulkProductResponse;
import com.stylenest.stylenest_backend.entity.BulkProduct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BulkProductMapper {

    private final ObjectMapper objectMapper;

    public BulkProductResponse toResponse(BulkProduct product) {

        List<String> images = product.getImageUrls() != null && !product.getImageUrls().isEmpty()
                ? new ArrayList<>(product.getImageUrls())
                : (product.getImageUrl() != null ? List.of(product.getImageUrl()) : List.of());

        List<BulkColorOption> colors = new ArrayList<>();
        if (product.getColorsJson() != null && !product.getColorsJson().isBlank()) {
            try {
                colors = objectMapper.readValue(product.getColorsJson(), new TypeReference<List<BulkColorOption>>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize colorsJson for bulk product {}: {}", product.getId(), e.getMessage());
            }
        }

        return BulkProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .imageUrls(images)
                .colors(colors)
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
