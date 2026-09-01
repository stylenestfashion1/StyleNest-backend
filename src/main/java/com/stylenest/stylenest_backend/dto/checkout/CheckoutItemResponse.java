package com.stylenest.stylenest_backend.dto.checkout;

import java.math.BigDecimal;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutItemResponse {

    private Long variantId;

    private String productName;

    private String color;

    private String size;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal subtotal;
}