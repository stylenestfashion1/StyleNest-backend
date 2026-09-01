package com.stylenest.stylenest_backend.dto.checkout;

import java.math.BigDecimal;
import java.util.List;

import com.stylenest.stylenest_backend.dto.address.AddressResponse;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutResponse {

    private List<CheckoutItemResponse> items;

    private AddressResponse shippingAddress;

    private BigDecimal totalAmount;
}