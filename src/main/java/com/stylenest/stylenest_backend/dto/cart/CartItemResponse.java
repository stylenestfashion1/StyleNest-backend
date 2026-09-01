package com.stylenest.stylenest_backend.dto.cart;

import java.math.BigDecimal;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {

    private Long cartItemId;

    private Long productId;

    private Long productVariantId;

    private String productName;

    private String color;

    private String size;

    private String imageUrl;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal subTotal;

}