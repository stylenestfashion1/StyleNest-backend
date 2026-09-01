package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.postal.PostalLookupResponse;

/**
 * Never throws -- checkout must never depend on this succeeding. Any
 * failure (bad code, unsupported country, network error, timeout) results
 * in a found=false response, not an exception.
 */
public interface PostalLookupService {

    PostalLookupResponse lookup(String countryCode, String postalCode);
}
