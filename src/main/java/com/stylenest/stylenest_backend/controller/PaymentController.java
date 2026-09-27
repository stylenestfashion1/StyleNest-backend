package com.stylenest.stylenest_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.payment.RazorpayGuestInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.RazorpayInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.RazorpayVerifyRequest;
import com.stylenest.stylenest_backend.dto.payment.RazorpayVerifyResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments/razorpay")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<RazorpayInitiateResponse>> initiate() {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Payment initiated",
                        paymentService.initiate()));
    }

    @PostMapping("/guest/initiate")
    public ResponseEntity<ApiResponse<RazorpayInitiateResponse>> initiateForGuest(
            @Valid @RequestBody RazorpayGuestInitiateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Payment initiated",
                        paymentService.initiateForGuest(request)));
    }

    /**
     * Called by our own frontend right after Razorpay Checkout's client-side
     * success handler fires -- shared by both registered and guest checkout,
     * since neither carries a StyleNest JWT requirement here: the internal
     * order is resolved from our own stored providerOrderId, and the
     * signature is verified server-side before anything is trusted. Must
     * stay public for the guest case; registered customers hit it the same
     * way for simplicity, since the request carries no user-scoped data.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<RazorpayVerifyResponse>> verify(
            @Valid @RequestBody RazorpayVerifyRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Payment verified",
                        paymentService.verifyPayment(request)));
    }

    /**
     * Razorpay posts here server-to-server. Must stay public and must
     * receive the exact raw body -- signature verification depends on the
     * literal bytes, so this deliberately takes a String rather than a
     * parsed DTO (any deserialize-then-reserialize step would risk not
     * byte-matching what Razorpay actually signed).
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String rawBody,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {

        paymentService.handleWebhook(rawBody, signature, eventId);

        return ResponseEntity.ok().build();
    }
}
