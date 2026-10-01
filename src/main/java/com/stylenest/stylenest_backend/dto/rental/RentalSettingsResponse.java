package com.stylenest.stylenest_backend.dto.rental;

import java.time.LocalDate;

import lombok.*;

/** Full settings shape -- Admin -> Rental (Navratri) -> Rental Settings only. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalSettingsResponse {

    private LocalDate seasonStartDate;
    private LocalDate seasonEndDate;
    private String whatsappNumber;
    private String paymentQrImageUrl;
    private String pickupInstructions;
    private String returnInstructions;
    private String lateReturnMessage;
    private String paymentInstructions;
    private String termsAndConditions;
    private Integer pendingPaymentExpiryMinutes;
}
