package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.payment.PaymentGuestInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.PaymentInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.PaymentVerifyRequest;
import com.stylenest.stylenest_backend.dto.payment.PaymentVerifyResponse;

public interface PaymentService {

    /** Registered checkout -- reserves stock against the current user's cart, then creates a gateway order for its server-resolved total. */
    PaymentInitiateResponse initiate();

    /** Guest checkout -- same as initiate(), but the order contents come from the request since a guest has no server-side cart. */
    PaymentInitiateResponse initiateForGuest(PaymentGuestInitiateRequest request);

    /**
     * Handles the frontend's call right after the gateway Checkout call
     * resolves. Independently confirms the payment's real state with the
     * gateway itself (never trusts the client alone) and applies it.
     * Idempotent: a repeat call for an already-resolved order is a no-op.
     */
    PaymentVerifyResponse verifyPayment(PaymentVerifyRequest request);

    /**
     * Handles a gateway webhook delivery. rawBody MUST be the exact,
     * unparsed request body (signature verification depends on the literal
     * bytes). idempotencyKey (Cashfree's x-idempotency-header) may be null
     * on an older webhook version -- idempotency then falls back entirely
     * to the order's own paymentStatus==PAID check.
     */
    void handleWebhook(String rawBody, String signatureHeader, String timestampHeader, String idempotencyKey);
}
