package com.stylenest.stylenest_backend.mapper;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.product.ProductImageResponse;
import com.stylenest.stylenest_backend.dto.product.ProductVariantRequest;
import com.stylenest.stylenest_backend.dto.product.ProductVariantResponse;
import com.stylenest.stylenest_backend.entity.ProductVariant;

@Component
public class ProductVariantMapper {

    public ProductVariant toEntity(ProductVariantRequest request) {

        return ProductVariant.builder()
                .color(request.getColor())
                .colorHex(request.getColorHex())
                .size(request.getSize())
                .stock(request.getStock())
                .build();
    }

    /**
     * Images are keyed by (product, color), not owned by this specific
     * size variant -- the caller looks them up by the variant's color and
     * passes the shared list in (every size of the same color renders the
     * same images).
     */
    public ProductVariantResponse toResponse(ProductVariant variant, List<ProductImageResponse> images) {

        return ProductVariantResponse.builder()
                .id(variant.getId())
                .color(variant.getColor())
                .colorHex(variant.getColorHex())
                .size(variant.getSize())
                .stock(variant.getStock())
                .sku(variant.getSku())
                .images(images == null ? Collections.emptyList() : images)
                .build();
    }

    public void updateEntity(ProductVariant variant, ProductVariantRequest request) {

        variant.setColor(request.getColor());
        variant.setColorHex(request.getColorHex());
        variant.setSize(request.getSize());
        variant.setStock(request.getStock());

    }
}