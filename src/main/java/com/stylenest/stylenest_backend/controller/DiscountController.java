package com.stylenest.stylenest_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.discount.DiscountClaimRequest;
import com.stylenest.stylenest_backend.dto.discount.DiscountClaimResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountQrVerifyRequest;
import com.stylenest.stylenest_backend.dto.discount.DiscountSessionResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.DiscountOfferService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Public in-store QR discount flow. "Public" here only means these routes
 * are permitAll at the Spring Security layer (there is no logged-in
 * customer involved, by design) -- real access control is enforced inside
 * DiscountOfferService: /verify requires the exact permanent QR secret
 * token, and /claim requires a valid, unexpired session token minted by
 * /verify. Neither endpoint accepts a discount value or status from the
 * client; the backend alone decides both.
 */
@Tag(name = "Discount / Rewards")
@RestController
@RequestMapping("/api/discount")
@RequiredArgsConstructor
public class DiscountController {

    public static final String SESSION_HEADER = "X-Discount-Session";

    private final DiscountOfferService discountOfferService;

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<DiscountSessionResponse>> verifyQrToken(
            @Valid @RequestBody DiscountQrVerifyRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "QR verified",
                        discountOfferService.verifyQrToken(request.getQrToken())));
    }

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<DiscountClaimResponse>> claim(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionToken,
            @Valid @RequestBody DiscountClaimRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Discount generated",
                        discountOfferService.claim(sessionToken, request)));
    }
}
