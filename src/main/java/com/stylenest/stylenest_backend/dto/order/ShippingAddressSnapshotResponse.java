package com.stylenest.stylenest_backend.dto.order;

import lombok.*;

/**
 * Built straight from an Order's own shipping* snapshot columns -- never
 * via AddressMapper/the live Address row, since a guest order has none and
 * a registered order's snapshot must not drift if the saved Address is
 * later edited.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingAddressSnapshotResponse {

    private String fullName;

    private String phone;

    private String phoneCountryCode;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String postalCode;

    private String country;

    private String countryCode;
}
