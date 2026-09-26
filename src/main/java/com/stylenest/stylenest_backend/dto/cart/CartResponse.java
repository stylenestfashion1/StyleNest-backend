package com.stylenest.stylenest_backend.dto.cart;

import java.math.BigDecimal;
import java.util.List;

import com.stylenest.stylenest_backend.enums.Currency;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {

    private Long cartId;

    // Null only for a genuinely empty cart that has never had an item
    // added. Every add-to-cart call must match this once it's set -- see
    // CartServiceImpl.addToCart.
    private Currency currency;

    private List<CartItemResponse> items;

    private BigDecimal totalPrice;

    private Integer totalItems;

}