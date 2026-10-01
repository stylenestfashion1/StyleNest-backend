package com.stylenest.stylenest_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stylenest.stylenest_backend.dto.rental.RentalActiveCatalogResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogPublicResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.RentalCatalogService;

import lombok.RequiredArgsConstructor;

/**
 * "Public" here only means permitAll at the Spring Security layer -- there
 * is no logged-in customer involved, by design (same pattern as
 * DiscountController/BulkCustomerController). Real access control is the
 * share token itself: unguessable (SecureRandom, 24 chars), and a wrong or
 * inactive token gets the exact same generic "not found" response either
 * way (see RentalCatalogServiceImpl.getPublicCatalogByShareToken).
 */
@RestController
@RequestMapping("/api/rental-catalogs")
@RequiredArgsConstructor
public class RentalCatalogController {

    private final RentalCatalogService rentalCatalogService;

    // Backs the main-site "Rentals" nav entry (a literal path segment, so
    // Spring routes it here rather than into {shareToken} below -- standard
    // "more specific match wins" precedence, same pattern as e.g. /users/me
    // vs /users/{id}). Never 404s -- see RentalCatalogServiceImpl.
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<RentalActiveCatalogResponse>> getActiveCatalog() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Active rental catalog fetched",
                        rentalCatalogService.getActiveCatalog()));
    }

    @GetMapping("/{shareToken}")
    public ResponseEntity<ApiResponse<RentalCatalogPublicResponse>> getByShareToken(
            @PathVariable String shareToken) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Rental catalog fetched",
                        rentalCatalogService.getPublicCatalogByShareToken(shareToken)));
    }
}
