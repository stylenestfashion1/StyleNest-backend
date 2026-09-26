package com.stylenest.stylenest_backend.dto.cart;

import com.stylenest.stylenest_backend.enums.Currency;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToCartRequest {

    @NotNull(message = "Product Variant Id is required")
    private Long productVariantId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    // The currency this item should be priced/added in. Must match the
    // cart's existing currency if it already has items -- see
    // CartServiceImpl.addToCart. The frontend's currency selector clears
    // the cart before letting a customer switch, so this mismatch should
    // only ever be reachable via a direct API call, not normal UI use.
    @NotNull(message = "Currency is required")
    private Currency currency;
}