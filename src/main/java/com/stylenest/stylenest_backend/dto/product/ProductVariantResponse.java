package com.stylenest.stylenest_backend.dto.product;

import java.util.List;

import com.stylenest.stylenest_backend.enums.Size;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantResponse {

    private Long id;

    private String color;

    private String colorHex;

    private Size size;

    private Integer stock;

    private String sku;

    private List<ProductImageResponse> images;
}