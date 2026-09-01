package com.stylenest.stylenest_backend.dto.product;

import java.util.List;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageResponse {

    private Long id;

    private String imageUrl;

    private Integer displayOrder;
    
    private List<ProductVariantResponse> variants;
}