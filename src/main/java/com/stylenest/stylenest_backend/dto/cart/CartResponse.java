package com.stylenest.stylenest_backend.dto.cart;

import java.math.BigDecimal;
import java.util.List;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {

    private Long cartId;

    private List<CartItemResponse> items;

    private BigDecimal totalPrice;

    private Integer totalItems;

}