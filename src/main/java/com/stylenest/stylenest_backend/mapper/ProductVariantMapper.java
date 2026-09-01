package com.stylenest.stylenest_backend.mapper;

import java.util.Collections;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.product.ProductVariantRequest;
import com.stylenest.stylenest_backend.dto.product.ProductVariantResponse;
import com.stylenest.stylenest_backend.entity.ProductVariant;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductVariantMapper {

    private final ProductImageMapper imageMapper;

    public ProductVariant toEntity(ProductVariantRequest request) {

        return ProductVariant.builder()
                .color(request.getColor())
                .size(request.getSize())
                .stock(request.getStock())
                .build();
    }

    public ProductVariantResponse toResponse(ProductVariant variant) {

        return ProductVariantResponse.builder()
                .id(variant.getId())
                .color(variant.getColor())
                .size(variant.getSize())
                .stock(variant.getStock())
                .images(
                        variant.getImages() == null
                                ? Collections.emptyList()
                                : variant.getImages()
                                        .stream()
                                        .map(imageMapper::toResponse)
                                        .collect(Collectors.toList())
                )
                .build();
    }

    public void updateEntity(ProductVariant variant, ProductVariantRequest request) {

        variant.setColor(request.getColor());
        variant.setSize(request.getSize());
        variant.setStock(request.getStock());

    }
}