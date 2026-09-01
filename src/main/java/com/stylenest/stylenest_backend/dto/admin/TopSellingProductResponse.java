package com.stylenest.stylenest_backend.dto.admin;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopSellingProductResponse {

    private Long productId;

    private String productName;

    private Long totalSold;

}