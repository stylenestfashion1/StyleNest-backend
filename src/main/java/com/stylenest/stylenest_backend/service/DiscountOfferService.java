package com.stylenest.stylenest_backend.service;

import org.springframework.data.domain.Page;

import com.stylenest.stylenest_backend.dto.admin.AdminDiscountOfferResponse;
import com.stylenest.stylenest_backend.dto.admin.AdminDiscountOfferSearchRequest;
import com.stylenest.stylenest_backend.dto.admin.AdminDiscountQrResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountClaimRequest;
import com.stylenest.stylenest_backend.dto.discount.DiscountClaimResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountSessionResponse;

public interface DiscountOfferService {

    /** Validates the permanent QR secret token and, if valid, issues a short-lived offer session token. */
    DiscountSessionResponse verifyQrToken(String rawQrToken);

    /** Validates the offer session token, then runs the full claim flow (see class javadoc on the impl). */
    DiscountClaimResponse claim(String sessionToken, DiscountClaimRequest request);

    Page<AdminDiscountOfferResponse> searchOffers(AdminDiscountOfferSearchRequest request);

    /** The full permanent special-offer URL (with the secret QR token) for the admin QR poster page. Admin-only. */
    AdminDiscountQrResponse getQrInfo();
}
