package com.stylenest.stylenest_backend.dto.rental;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Sent as the "data" part of a multipart request alongside an optional screenshot file. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalPaymentSubmitRequest {

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
}
