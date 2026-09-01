package com.stylenest.stylenest_backend.dto.admin;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LowStockProductResponse {

    private Long productId;

    private String productName;

    private String color;

    private String size;

    private Integer stock;

}