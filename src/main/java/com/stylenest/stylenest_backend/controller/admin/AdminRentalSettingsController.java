package com.stylenest.stylenest_backend.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.stylenest.stylenest_backend.dto.rental.RentalSettingsResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalSettingsUpdateRequest;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.RentalSettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** No explicit SecurityConfig matcher needed -- covered by the existing /api/admin/** hasRole("ADMIN") rule. */
@RestController
@RequestMapping("/api/admin/rental-settings")
@RequiredArgsConstructor
public class AdminRentalSettingsController {

    private final RentalSettingsService rentalSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<RentalSettingsResponse>> getSettings() {

        return ResponseEntity.ok(ApiResponse.success("Rental settings fetched", rentalSettingsService.getSettings()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<RentalSettingsResponse>> updateSettings(
            @Valid @RequestBody RentalSettingsUpdateRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("Rental settings updated", rentalSettingsService.updateSettings(request)));
    }

    @PostMapping(value = "/payment-qr/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<RentalSettingsResponse>> uploadPaymentQr(
            @RequestParam("file") MultipartFile file) {

        return ResponseEntity.ok(
                ApiResponse.success("Payment QR updated", rentalSettingsService.uploadPaymentQr(file)));
    }
}
