package com.stylenest.stylenest_backend.dto.address;

import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.AddressType;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {

    private Long id;

    private String fullName;

    private String phone;

    private String phoneCountryCode;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String country;

    private String countryCode;

    private String postalCode;

    private AddressType addressType;

    private Boolean isDefault;

    private LocalDateTime createdAt;
}