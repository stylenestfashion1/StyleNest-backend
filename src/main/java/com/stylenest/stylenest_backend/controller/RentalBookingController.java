package com.stylenest.stylenest_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.stylenest.stylenest_backend.dto.rental.RentalBookingCreateRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalBookingResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalPaymentSubmitRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalPublicSettingsResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalUnavailableDatesResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.RentalBookingService;
import com.stylenest.stylenest_backend.service.RentalSettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Public (permitAll) booking flow for a WhatsApp-link visitor -- no login,
 * no StyleNest customer account, ever (see spec sections 8 and 24). Real
 * access control at every level is the catalog's own share token, exactly
 * like RentalCatalogController -- see RentalBookingServiceImpl's
 * findPublicItem/findBookingScopedToShareToken.
 */
@RestController
@RequestMapping("/api/rental-catalogs/{shareToken}")
@RequiredArgsConstructor
public class RentalBookingController {

    private final RentalBookingService rentalBookingService;
    private final RentalSettingsService rentalSettingsService;

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<RentalPublicSettingsResponse>> getSettings(
            @PathVariable String shareToken) {

        return ResponseEntity.ok(
                ApiResponse.success("Rental settings fetched", rentalSettingsService.getPublicSettings(shareToken)));
    }

    @GetMapping("/items/{itemId}/unavailable-dates")
    public ResponseEntity<ApiResponse<RentalUnavailableDatesResponse>> getUnavailableDates(
            @PathVariable String shareToken, @PathVariable Long itemId) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Unavailable dates fetched",
                        rentalBookingService.getUnavailableDates(shareToken, itemId)));
    }

    @PostMapping("/items/{itemId}/bookings")
    public ResponseEntity<ApiResponse<RentalBookingResponse>> createBooking(
            @PathVariable String shareToken,
            @PathVariable Long itemId,
            @Valid @RequestBody RentalBookingCreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        "Booking created",
                        rentalBookingService.createBooking(shareToken, itemId, request)));
    }

    @PostMapping(value = "/bookings/{bookingReference}/payment", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<RentalBookingResponse>> submitPayment(
            @PathVariable String shareToken,
            @PathVariable String bookingReference,
            @Valid @ModelAttribute RentalPaymentSubmitRequest request,
            @RequestParam(value = "screenshot", required = false) MultipartFile screenshot) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Payment details submitted",
                        rentalBookingService.submitPayment(
                                shareToken, bookingReference, request.getTransactionId(), screenshot)));
    }
}
