package com.stylenest.stylenest_backend.service;

import java.math.BigDecimal;

import com.stylenest.stylenest_backend.enums.Currency;

/**
 * Abstraction over "whichever payment gateway is currently active" --
 * PaymentServiceImpl and OrderServiceImpl talk to this interface only,
 * never to a provider SDK directly, so replacing the gateway again in the
 * future (as this project just did once already, Easebuzz -> Razorpay)
 * never requires touching order/checkout logic, only a new implementation
 * of this interface. Only one implementation exists today
 * (RazorpayPaymentProviderClient) -- this is not a hypothetical
 * multi-provider system, just the seam that makes the current provider
 * swappable without a rewrite.
 */
public interface PaymentProviderClient {

    /** The gateway's PUBLIC key id -- safe to hand to the frontend. Never the key secret or webhook secret. */
    String publicKeyId();

    /**
     * Creates a gateway order for the given server-resolved amount/currency.
     * The caller is responsible for ensuring amount/currency come from the
     * database, never from client input.
     */
    ProviderOrder createOrder(BigDecimal amount, Currency currency, String receipt);

    /**
     * Verifies the payment signature returned by the gateway's client-side
     * checkout callback. This alone is NOT sufficient to mark an order paid
     * -- see fetchPayment, which independently confirms the payment's real
     * status server-to-server.
     */
    boolean verifyPaymentSignature(String providerOrderId, String providerPaymentId, String signature);

    /** Authoritative, server-to-server fetch of a payment's current state. */
    ProviderPayment fetchPayment(String providerPaymentId);

    /**
     * Explicitly captures an authorized-but-not-yet-captured payment for
     * the exact server-known amount, rather than assuming the gateway
     * Dashboard's auto-capture setting is on.
     */
    ProviderPayment capturePayment(String providerPaymentId, BigDecimal amount, Currency currency);

    /**
     * Verifies a webhook's HMAC signature. rawBody must be the exact,
     * unmodified request body bytes (as a string) -- never a re-serialized
     * or partially-parsed version, since the signature covers the literal
     * bytes the gateway sent.
     */
    boolean verifyWebhookSignature(String rawBody, String signatureHeader);

    record ProviderOrder(String id, String status) {}

    record ProviderPayment(
            String id,
            String orderId,
            String status,
            String method,
            String errorReason) {}
}
