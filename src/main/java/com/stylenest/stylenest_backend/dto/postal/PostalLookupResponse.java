package com.stylenest.stylenest_backend.dto.postal;

import lombok.*;

/**
 * Field names line up with Address's city/state/country so the frontend
 * can prefill directly. `found=false` on any failure (invalid code,
 * unsupported country, network error, timeout) -- never an error response;
 * this is a convenience/auto-suggest feature, city/state must stay
 * editable regardless.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostalLookupResponse {

    private boolean found;

    private String city;

    private String state;

    private String country;

    public static PostalLookupResponse notFound() {
        return PostalLookupResponse.builder().found(false).build();
    }
}
