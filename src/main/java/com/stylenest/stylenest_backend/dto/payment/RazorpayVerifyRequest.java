package com.stylenest.stylenest_backend.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Exactly the three values Razorpay Checkout's client-side success handler
 * receives. razorpayOrderId is the TRUSTED lookup key used to resolve
 * which internal Order this is about (via Payment.providerOrderId) --
 * there is deliberately no internal order/user id field here for the
 * backend to blindly trust instead. Shared by both the registered and
 * guest flows, since verification only ever depends on these three
 * values, never on who's asking.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayVerifyRequest {

    @NotBlank(message = "razorpayOrderId is required")
    private String razorpayOrderId;

    @NotBlank(message = "razorpayPaymentId is required")
    private String razorpayPaymentId;

    @NotBlank(message = "razorpaySignature is required")
    private String razorpaySignature;
}
