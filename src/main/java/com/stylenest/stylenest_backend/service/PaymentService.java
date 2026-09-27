package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.payment.RazorpayGuestInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.RazorpayInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.RazorpayVerifyRequest;
import com.stylenest.stylenest_backend.dto.payment.RazorpayVerifyResponse;

public interface PaymentService {

    /** Registered checkout -- reserves stock against the current user's cart, then creates a Razorpay order for its server-resolved total. */
    RazorpayInitiateResponse initiate();

    /** Guest checkout -- same as initiate(), but the order contents come from the request since a guest has no server-side cart. */
    RazorpayInitiateResponse initiateForGuest(RazorpayGuestInitiateRequest request);

    /**
     * Handles Razorpay Checkout's client-side success callback. Verifies
     * the signature, then independently confirms the payment's real state
     * with Razorpay itself (never trusts the signature/callback alone) and
     * captures it if it's only authorized. Idempotent: a repeat call for
     * an already-resolved order is a no-op.
     */
    RazorpayVerifyResponse verifyPayment(RazorpayVerifyRequest request);

    /**
     * Handles a Razorpay webhook delivery. rawBody MUST be the exact,
     * unparsed request body (signature verification depends on the literal
     * bytes) and signatureHeader the X-Razorpay-Signature header value.
     * Idempotent against Razorpay's own retries/duplicate deliveries.
     */
    void handleWebhook(String rawBody, String signatureHeader, String eventId);
}
