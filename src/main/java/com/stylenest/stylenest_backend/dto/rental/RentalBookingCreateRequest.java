package com.stylenest.stylenest_backend.dto.rental;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalBookingCreateRequest {

    @NotNull(message = "Pickup date is required")
    private LocalDate startDate;

    @NotNull(message = "Return date is required")
    private LocalDate endDate;

    @NotBlank(message = "Name is required")
    private String customerName;

    @NotBlank(message = "WhatsApp/mobile number is required")
    private String customerPhone;

    // The customer must have ticked "I Agree & Continue" on the terms
    // screen before this request is ever sent -- rejected server-side
    // (not just hidden in the UI) if false/missing. See
    // RentalBookingServiceImpl.createBooking.
    @AssertTrue(message = "You must accept the rental terms to continue")
    private boolean termsAccepted;
}
