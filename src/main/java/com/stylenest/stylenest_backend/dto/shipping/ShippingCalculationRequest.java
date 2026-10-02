package com.stylenest.stylenest_backend.dto.shipping;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingCalculationRequest {

    @NotBlank(message = "Destination postal code is required.")
    private String postalCode;

    private String city;

    private String state;

    private String countryCode;

    private List<ShippingItemRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShippingItemRequest {
        private Long variantId;
        private Integer quantity;
    }
}
