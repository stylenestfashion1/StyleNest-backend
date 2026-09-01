package com.stylenest.stylenest_backend.dto.address;

import com.stylenest.stylenest_backend.enums.AddressType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    private String phone;

    // International calling code, e.g. "+91", "+1" -- optional.
    private String phoneCountryCode;

    @NotBlank
    private String addressLine1;

    private String addressLine2;

    @NotBlank
    private String city;

    @NotBlank
    private String state;

    @NotBlank
    private String country;

    // ISO 3166-1 alpha-2, e.g. "IN", "US" -- optional.
    private String countryCode;

    @NotBlank
    private String postalCode;

    @NotNull
    private AddressType addressType;

    @Builder.Default
    private Boolean isDefault = false;
}