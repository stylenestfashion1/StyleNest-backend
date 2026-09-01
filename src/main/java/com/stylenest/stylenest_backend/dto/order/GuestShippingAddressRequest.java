package com.stylenest.stylenest_backend.dto.order;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestShippingAddressRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Phone is required")
    private String phone;

    // e.g. "+91", "+1" -- optional, but should be supplied by the frontend
    // based on the selected country.
    private String phoneCountryCode;

    @NotBlank(message = "Address line 1 is required")
    private String addressLine1;

    private String addressLine2;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State/Province/Region is required")
    private String state;

    @NotBlank(message = "ZIP/Postal code is required")
    private String postalCode;

    @NotBlank(message = "Country is required")
    private String country;

    // ISO 3166-1 alpha-2, e.g. "IN", "US" -- optional.
    private String countryCode;
}
