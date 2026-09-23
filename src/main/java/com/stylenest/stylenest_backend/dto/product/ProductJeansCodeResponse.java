package com.stylenest.stylenest_backend.dto.product;

import lombok.*;

// Deliberately its own tiny DTO, never merged into ProductResponse: that
// DTO is shared with the public, unauthenticated GET /api/products/**
// endpoints (see ProductController), so anything added there becomes
// customer-visible. Only served from admin-only endpoints -- see
// AdminProductController.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductJeansCodeResponse {

    private Long productId;

    private String jeansCode;
}
