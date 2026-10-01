package com.stylenest.stylenest_backend.service;

import java.math.BigDecimal;

import com.stylenest.stylenest_backend.enums.Currency;

/**
 * Abstraction over "whichever payment gateway is currently active" --
 * PaymentServiceImpl and OrderServiceImpl talk to this interface only,
 * never to a provider SDK/REST API directly, so replacing the gateway
 * again in the future (as this project has now done twice: Easebuzz ->
 * Razorpay -> Cashfree) never requires touching order/checkout logic,
 * only a new implementation of this interface. Only one implementation
 * exists today (CashfreePaymentProviderClient) -- this is not a
 * hypothetical multi-provider system, just the seam that makes the
 * current provider swappable without a rewrite.
 */
public interface PaymentProviderClient {

    /**
     * Creates a gateway order for the given server-resolved amount/currency
     * and our own order reference (becomes the gateway's order id). The
     * caller is responsible for ensuring amount/currency come from the
     * database, never from client input. customer is required by some
     * gateways (e.g. Cashfree) even though the checkout UI itself collects
     * no payment details from us directly.
     */
    ProviderOrder createOrder(BigDecimal amount, Currency currency, String orderReference, CustomerDetails customer);

    /**
     * A gateway "create order" call for an order_id that already exists
     * fails (Cashfree: "order_already_exists") rather than refreshing
     * anything -- this re-fetches the still-valid payment_session_id for
     * an existing, still-ACTIVE gateway order instead, for the case where
     * a Payment row already exists (e.g. the customer refreshed the
     * payment page or retried) and createOrder must not be called again.
     */
    String refreshPaymentSession(String providerOrderId);

    /**
     * Authoritative, server-to-server fetch of an order's current payment
     * outcome, keyed by OUR OWN order reference (never a client-supplied
     * internal id). This alone is what a "verify" call and a webhook
     * delivery both ultimately confirm against -- neither trusts the
     * client's or the webhook payload's claimed status without this.
     */
    ProviderOrderStatus fetchOrderStatus(String providerOrderId);

    /**
     * Verifies a webhook's HMAC signature. rawBody must be the exact,
     * unmodified request body bytes (as a string) -- never a re-serialized
     * or partially-parsed version, since the signature covers the literal
     * bytes the gateway sent. timestampHeader is required by gateways
     * (e.g. Cashfree) that sign timestamp+body together to guard against
     * replay.
     */
    boolean verifyWebhookSignature(String rawBody, String signatureHeader, String timestampHeader);

    /**
     * Parses an already-signature-verified webhook payload into a
     * normalized result. Payload shape is entirely gateway-specific (event
     * names, JSON structure) -- callers never parse rawBody themselves.
     */
    WebhookEventResult parseWebhookEvent(String rawBody);

    record CustomerDetails(String customerId, String customerName, String customerEmail, String customerPhone) {}

    record ProviderOrder(String providerOrderId, String paymentSessionId) {}

    record ProviderOrderStatus(PaymentOutcome outcome, String providerPaymentId, String rawStatus, String failureReason) {}

    record WebhookEventResult(String providerOrderId, PaymentOutcome outcome, String providerPaymentId, String failureReason) {}

    enum PaymentOutcome { SUCCESS, FAILURE, PENDING, IRRELEVANT }
}
