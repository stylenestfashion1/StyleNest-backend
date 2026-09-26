package com.stylenest.stylenest_backend.dto.checkout;

import java.math.BigDecimal;
import java.util.List;

import com.stylenest.stylenest_backend.dto.address.AddressResponse;
import com.stylenest.stylenest_backend.enums.Currency;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutResponse {

    private List<CheckoutItemResponse> items;

    private AddressResponse shippingAddress;

    // Null only for a genuinely empty cart -- lets the frontend show the
    // USD-checkout-blocked state before the customer attempts to place an
    // order, not just after the backend rejects it.
    private Currency currency;

    private BigDecimal totalAmount;
}