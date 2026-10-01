package com.stylenest.stylenest_backend.controller.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.rental.RentalBookingResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalBookingSummaryResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCancelRequest;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.RentalBookingService;

import lombok.RequiredArgsConstructor;

/** No explicit SecurityConfig matcher needed -- covered by the existing /api/admin/** hasRole("ADMIN") rule. */
@RestController
@RequestMapping("/api/admin/rental-bookings")
@RequiredArgsConstructor
public class AdminRentalBookingController {

    private final RentalBookingService rentalBookingService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RentalBookingSummaryResponse>>> getAllBookings() {

        return ResponseEntity.ok(ApiResponse.success("Rental bookings fetched", rentalBookingService.getAllBookings()));
    }

    @GetMapping("/{bookingReference}")
    public ResponseEntity<ApiResponse<RentalBookingResponse>> getBooking(@PathVariable String bookingReference) {

        return ResponseEntity.ok(
                ApiResponse.success("Rental booking fetched", rentalBookingService.getBookingByReference(bookingReference)));
    }

    @PostMapping("/{bookingReference}/confirm")
    public ResponseEntity<ApiResponse<RentalBookingResponse>> confirmBooking(@PathVariable String bookingReference) {

        return ResponseEntity.ok(
                ApiResponse.success("Booking confirmed", rentalBookingService.confirmBooking(bookingReference)));
    }

    // Empty/absent body cancels the entire booking; a body with
    // startDate+endDate cancels only that sub-range -- see
    // RentalCancelRequest's javadoc.
    @PostMapping("/{bookingReference}/cancel")
    public ResponseEntity<ApiResponse<RentalBookingResponse>> cancelBooking(
            @PathVariable String bookingReference,
            @RequestBody(required = false) RentalCancelRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("Booking cancelled", rentalBookingService.cancelBooking(bookingReference, request)));
    }

    @PostMapping("/{bookingReference}/complete")
    public ResponseEntity<ApiResponse<RentalBookingResponse>> markCompleted(@PathVariable String bookingReference) {

        return ResponseEntity.ok(
                ApiResponse.success("Booking marked completed", rentalBookingService.markCompleted(bookingReference)));
    }
}
