package com.stylenest.stylenest_backend.dto.rental;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalSettingsUpdateRequest {

    @NotNull(message = "Season start date is required")
    private LocalDate seasonStartDate;

    @NotNull(message = "Season end date is required")
    private LocalDate seasonEndDate;

    @NotBlank(message = "WhatsApp number is required")
    private String whatsappNumber;

    @NotBlank(message = "Pickup instructions are required")
    private String pickupInstructions;

    @NotBlank(message = "Return instructions are required")
    private String returnInstructions;

    @NotBlank(message = "Late return message is required")
    private String lateReturnMessage;

    @NotBlank(message = "Payment instructions are required")
    private String paymentInstructions;

    @NotBlank(message = "Terms & conditions are required")
    private String termsAndConditions;

    @NotNull(message = "Pending payment expiry is required")
    @Min(value = 1, message = "Pending payment expiry must be at least 1 minute")
    private Integer pendingPaymentExpiryMinutes;
}
