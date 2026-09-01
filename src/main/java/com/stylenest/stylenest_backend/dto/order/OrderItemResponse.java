package com.stylenest.stylenest_backend.dto.order;

import java.math.BigDecimal;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {

    private Long productId;

    private Long productVariantId;

    private String productName;

    private String color;

    private String size;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal subtotal;
}