package com.stylenest.stylenest_backend.dto.product;

import java.util.List;

import com.stylenest.stylenest_backend.enums.Color;
import com.stylenest.stylenest_backend.enums.Size;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantResponse {

    private Long id;

    private Color color;

    private Size size;

    private Integer stock;

    private List<ProductImageResponse> images;
}