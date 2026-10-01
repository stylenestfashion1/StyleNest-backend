package com.stylenest.stylenest_backend.dto.rental;

import java.time.LocalDate;

import lombok.*;

/**
 * Customer-facing subset of {@link RentalSettingsResponse} -- deliberately
 * leaves out pendingPaymentExpiryMinutes (internal operational detail, not
 * something a customer needs or should see).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalPublicSettingsResponse {

    private LocalDate seasonStartDate;
    private LocalDate seasonEndDate;
    private String whatsappNumber;
    private String paymentQrImageUrl;
    private String pickupInstructions;
    private String returnInstructions;
    private String lateReturnMessage;
    private String paymentInstructions;
    private String termsAndConditions;
}
