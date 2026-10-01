package com.stylenest.stylenest_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.payment.PaymentGuestInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.PaymentInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.PaymentVerifyRequest;
import com.stylenest.stylenest_backend.dto.payment.PaymentVerifyResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentInitiateResponse>> initiate() {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Payment initiated",
                        paymentService.initiate()));
    }

    @PostMapping("/guest/initiate")
    public ResponseEntity<ApiResponse<PaymentInitiateResponse>> initiateForGuest(
            @Valid @RequestBody PaymentGuestInitiateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Payment initiated",
                        paymentService.initiateForGuest(request)));
    }

    /**
     * Called by our own frontend right after the Cashfree Checkout call
     * resolves -- shared by both registered and guest checkout, since
     * neither carries a StyleNest JWT requirement here: the internal
     * order is resolved from our own stored providerOrderId, and the real
     * outcome is always independently re-confirmed server-to-server with
     * Cashfree before anything is trusted (see PaymentServiceImpl). Must
     * stay public for the guest case; registered customers hit it the
     * same way for simplicity, since the request carries no user-scoped
     * data.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentVerifyResponse>> verify(
            @Valid @RequestBody PaymentVerifyRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Payment verified",
                        paymentService.verifyPayment(request)));
    }

    /**
     * Cashfree posts here server-to-server. Must stay public and must
     * receive the exact raw body -- signature verification depends on the
     * literal bytes, so this deliberately takes a String rather than a
     * parsed DTO (any deserialize-then-reserialize step would risk not
     * byte-matching what Cashfree actually signed). x-idempotency-header is
     * Cashfree's unique-per-delivery id (webhook versions 2025-01-01+),
     * used the same way the previous gateway's event id was -- see
     * PaymentServiceImpl. Optional because an older webhook version
     * without it must still be processable; the order's own
     * paymentStatus==PAID check is the real backstop either way.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String rawBody,
            @RequestHeader("x-webhook-signature") String signature,
            @RequestHeader("x-webhook-timestamp") String timestamp,
            @RequestHeader(value = "x-idempotency-header", required = false) String idempotencyKey) {

        paymentService.handleWebhook(rawBody, signature, timestamp, idempotencyKey);

        return ResponseEntity.ok().build();
    }
}
