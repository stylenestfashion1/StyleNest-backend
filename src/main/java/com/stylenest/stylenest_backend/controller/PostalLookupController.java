package com.stylenest.stylenest_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.postal.PostalLookupResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.PostalLookupService;

import lombok.RequiredArgsConstructor;

/**
 * Public and read-only (no PII) -- needed pre-auth so guest checkout can
 * use it too.
 */
@RestController
@RequestMapping("/api/postal-lookup")
@RequiredArgsConstructor
public class PostalLookupController {

    private final PostalLookupService postalLookupService;

    @GetMapping
    public ResponseEntity<ApiResponse<PostalLookupResponse>> lookup(
            @RequestParam String countryCode,
            @RequestParam String postalCode) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Postal lookup completed",
                        postalLookupService.lookup(countryCode, postalCode)));
    }
}
