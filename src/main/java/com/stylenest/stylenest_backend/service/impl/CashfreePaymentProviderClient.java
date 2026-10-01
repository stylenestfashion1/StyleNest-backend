package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.exception.PaymentGatewayException;
import com.stylenest.stylenest_backend.service.PaymentProviderClient;

/**
 * The only class in this codebase that talks to the Cashfree Payment
 * Gateway REST API directly -- everything else (PaymentServiceImpl,
 * OrderServiceImpl) goes through the PaymentProviderClient interface.
 * Plain REST via Spring's RestClient (no Cashfree SDK dependency) --
 * the API surface used here is small, simple JSON, and this mirrors how
 * PostalLookupService already talks to other external REST APIs in this
 * codebase, keeping the dependency footprint unchanged.
 *
 * Request/response shapes, header names, and the webhook signature
 * formula here are exactly what Cashfree's official 2026 API reference
 * documents (api-reference/payments/latest) -- verified against the live
 * docs while writing this, not assumed from older Razorpay-era patterns.
 */
@Service
public class CashfreePaymentProviderClient implements PaymentProviderClient {

    private static final String PROD_BASE_URL = "https://api.cashfree.com/pg";
    private static final String SANDBOX_BASE_URL = "https://sandbox.cashfree.com/pg";

    private final String clientId;
    private final String clientSecret;
    private final String apiVersion;
    private final String baseUrl;
    private final String returnUrl;
    private final String notifyUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Built lazily (not eagerly at startup) so a StyleNest environment
    // with no Cashfree keys configured yet (e.g. a fresh local/test setup)
    // can still start up cleanly -- the "not configured" case is only
    // ever surfaced when a payment is actually attempted, same pattern
    // the previous Razorpay/Easebuzz integrations used.
    private RestClient restClient;

    public CashfreePaymentProviderClient(
            @Value("${cashfree.client-id:}") String clientId,
            @Value("${cashfree.client-secret:}") String clientSecret,
            @Value("${cashfree.api-version:2025-01-01}") String apiVersion,
            @Value("${cashfree.mode:production}") String mode,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.base-url}") String appBaseUrl) {

        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.apiVersion = apiVersion;
        this.baseUrl = "sandbox".equalsIgnoreCase(mode) ? SANDBOX_BASE_URL : PROD_BASE_URL;

        // order_meta.return_url is only actually navigated to if the
        // gateway ever falls back to a full-page redirect (checkout is
        // opened in "_modal" mode by the frontend, so this is a safety
        // net, not the primary flow); notify_url is Cashfree's own
        // secondary "server-to-server even if the customer's connection
        // drops" callback, separate from (and in addition to) the webhook
        // configured in the Cashfree Dashboard, pointed at the same
        // endpoint since both should trigger identical, idempotent handling.
        this.returnUrl = frontendUrl + "/orders";
        this.notifyUrl = appBaseUrl + "/api/payments/webhook";
    }

    private synchronized RestClient client() {

        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new PaymentGatewayException(
                    "Online payments are not configured yet. Please use Cash on Delivery for now.");
        }

        if (restClient == null) {
            restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("x-client-id", clientId)
                    .defaultHeader("x-client-secret", clientSecret)
                    .defaultHeader("x-api-version", apiVersion)
                    .defaultHeader("Content-Type", "application/json")
                    .build();
        }

        return restClient;
    }

    @Override
    public ProviderOrder createOrder(BigDecimal amount, Currency currency, String orderReference, CustomerDetails customer) {

        try {

            // customer_name/customer_email are optional per Cashfree's schema
            // but must be 3-100 chars WHEN present -- omitted entirely rather
            // than sent as an empty string, which would fail that length
            // check instead of just being treated as absent.
            Map<String, Object> customerDetails = new java.util.LinkedHashMap<>();
            customerDetails.put("customer_id", customer.customerId());
            customerDetails.put("customer_phone", customer.customerPhone());
            if (!isBlank(customer.customerName())) {
                customerDetails.put("customer_name", customer.customerName());
            }
            if (!isBlank(customer.customerEmail())) {
                customerDetails.put("customer_email", customer.customerEmail());
            }

            Map<String, Object> orderMeta = Map.of(
                    "return_url", returnUrl,
                    "notify_url", notifyUrl);

            Map<String, Object> requestBody = Map.of(
                    "order_id", orderReference,
                    "order_amount", amount,
                    "order_currency", currency.name(),
                    "customer_details", customerDetails,
                    "order_meta", orderMeta);

            JsonNode response = client().post()
                    .uri("/orders")
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            String cfOrderId = response.path("order_id").asText(orderReference);
            String paymentSessionId = response.path("payment_session_id").asText(null);

            if (paymentSessionId == null) {
                throw new PaymentGatewayException("Could not start the payment. Please try again.");
            }

            return new ProviderOrder(cfOrderId, paymentSessionId);

        } catch (RestClientException e) {

            throw new PaymentGatewayException("Could not start the payment. Please try again.");
        }
    }

    @Override
    public String refreshPaymentSession(String providerOrderId) {

        try {

            JsonNode response = client().get()
                    .uri("/orders/{orderId}", providerOrderId)
                    .retrieve()
                    .body(JsonNode.class);

            String orderStatus = response.path("order_status").asText("");
            String paymentSessionId = response.path("payment_session_id").asText(null);

            if (!"ACTIVE".equals(orderStatus) || paymentSessionId == null) {
                throw new PaymentGatewayException(
                        "This payment session is no longer valid. Please start checkout again.");
            }

            return paymentSessionId;

        } catch (RestClientException e) {

            throw new PaymentGatewayException("Could not resume the payment. Please try again.");
        }
    }

    @Override
    public ProviderOrderStatus fetchOrderStatus(String providerOrderId) {

        try {

            JsonNode payments = client().get()
                    .uri("/orders/{orderId}/payments", providerOrderId)
                    .retrieve()
                    .body(JsonNode.class);

            return toOrderStatus(payments);

        } catch (RestClientException e) {

            throw new PaymentGatewayException("Could not verify the payment. Please try again.");
        }
    }

    /**
     * Cashfree allows multiple sequential payment attempts per order
     * (e.g. a declined card followed by a successful UPI retry), exactly
     * like the previous gateway did -- this list is every attempt so far,
     * newest first per Cashfree's own ordering. A single SUCCESS anywhere
     * in the list is authoritative regardless of position; otherwise the
     * most recent attempt's outcome is what's currently true.
     */
    private ProviderOrderStatus toOrderStatus(JsonNode payments) {

        if (payments == null || !payments.isArray() || payments.isEmpty()) {
            return new ProviderOrderStatus(PaymentOutcome.PENDING, null, "NOT_ATTEMPTED", null);
        }

        for (JsonNode payment : payments) {
            if ("SUCCESS".equals(payment.path("payment_status").asText())) {
                return new ProviderOrderStatus(
                        PaymentOutcome.SUCCESS,
                        payment.path("cf_payment_id").asText(null),
                        "SUCCESS",
                        null);
            }
        }

        JsonNode latest = payments.get(0);
        String rawStatus = latest.path("payment_status").asText("PENDING");

        PaymentOutcome outcome = switch (rawStatus) {
            case "FAILED", "CANCELLED", "VOID" -> PaymentOutcome.FAILURE;
            default -> PaymentOutcome.PENDING; // PENDING, USER_DROPPED, NOT_ATTEMPTED
        };

        return new ProviderOrderStatus(
                outcome,
                latest.path("cf_payment_id").asText(null),
                rawStatus,
                latest.path("payment_message").asText(null));
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader, String timestampHeader) {

        if (clientSecret.isBlank() || isBlank(rawBody) || isBlank(signatureHeader) || isBlank(timestampHeader)) {
            return false;
        }

        try {

            String signedPayload = timestampHeader + rawBody;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));

            String expectedSignature = Base64.getEncoder().encodeToString(hmacBytes);

            return constantTimeEquals(expectedSignature, signatureHeader);

        } catch (Exception e) {

            // A malformed header throwing during verification is just as
            // much "not verified" as one that verifies to false.
            return false;
        }
    }

    @Override
    public WebhookEventResult parseWebhookEvent(String rawBody) {

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            return new WebhookEventResult(null, PaymentOutcome.IRRELEVANT, null, null);
        }

        String type = payload.path("type").asText("");
        JsonNode order = payload.path("data").path("order");
        JsonNode payment = payload.path("data").path("payment");

        String providerOrderId = order.path("order_id").asText(null);

        PaymentOutcome outcome = switch (type) {
            case "PAYMENT_SUCCESS_WEBHOOK" -> PaymentOutcome.SUCCESS;
            case "PAYMENT_FAILED_WEBHOOK", "PAYMENT_USER_DROPPED_WEBHOOK" -> PaymentOutcome.FAILURE;
            default -> PaymentOutcome.IRRELEVANT;
        };

        return new WebhookEventResult(
                providerOrderId,
                outcome,
                payment.path("cf_payment_id").asText(null),
                payment.path("payment_message").asText(null));
    }

    private boolean constantTimeEquals(String a, String b) {

        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
