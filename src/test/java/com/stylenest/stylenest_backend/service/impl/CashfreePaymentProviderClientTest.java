package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.exception.PaymentGatewayException;
import com.stylenest.stylenest_backend.service.PaymentProviderClient.CustomerDetails;
import com.stylenest.stylenest_backend.service.PaymentProviderClient.PaymentOutcome;
import com.stylenest.stylenest_backend.service.PaymentProviderClient.WebhookEventResult;

/**
 * Exercises the actual cryptographic verification logic (Base64
 * HMAC-SHA256 over timestamp+body, per Cashfree's own documented formula)
 * and webhook payload parsing with no mocking and no network call --
 * genuinely testable without real Cashfree credentials. createOrder/
 * fetchOrderStatus, which DO make real HTTP calls, are only exercised for
 * their "not configured" fail-fast path here; the actual gateway
 * round-trip is covered by manual/E2E testing with Cashfree sandbox
 * credentials, same as the previous gateway's live-call paths always were.
 */
class CashfreePaymentProviderClientTest {

    private static final String CLIENT_SECRET = "test-client-secret";

    private final CashfreePaymentProviderClient client = new CashfreePaymentProviderClient(
            "test-client-id", CLIENT_SECRET, "2025-01-01", "production",
            "https://stylenestfashion.com", "https://api.stylenestfashion.com");

    @Test
    void createOrder_whenGatewayNotConfigured_failsCleanlyWithoutAnyNetworkCall() {

        CashfreePaymentProviderClient unconfigured = new CashfreePaymentProviderClient(
                "", "", "2025-01-01", "production",
                "https://stylenestfashion.com", "https://api.stylenestfashion.com");

        CustomerDetails customer = new CustomerDetails("cust-1", "Test User", "test@example.com", "9999999999");

        assertThatThrownBy(() -> unconfigured.createOrder(BigDecimal.TEN, Currency.INR, "SN-1", customer))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void fetchOrderStatus_whenGatewayNotConfigured_failsCleanlyWithoutAnyNetworkCall() {

        CashfreePaymentProviderClient unconfigured = new CashfreePaymentProviderClient(
                "", "", "2025-01-01", "production",
                "https://stylenestfashion.com", "https://api.stylenestfashion.com");

        assertThatThrownBy(() -> unconfigured.fetchOrderStatus("SN-1"))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void verifyWebhookSignature_genuineHmacOverTimestampAndRawBody_isAccepted() {

        String rawBody = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\",\"data\":{}}";
        String timestamp = "1735689600";
        String signature = base64HmacSha256(timestamp + rawBody, CLIENT_SECRET);

        assertThat(client.verifyWebhookSignature(rawBody, signature, timestamp)).isTrue();
    }

    @Test
    void verifyWebhookSignature_bodyModifiedAfterSigning_isRejected() {

        String originalBody = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\",\"data\":{}}";
        String timestamp = "1735689600";
        String signature = base64HmacSha256(timestamp + originalBody, CLIENT_SECRET);

        // Simulates a man-in-the-middle or logging/proxy layer altering the
        // body after Cashfree signed it -- the signature must not validate
        // against a different body.
        String tamperedBody = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\",\"data\":{\"tampered\":true}}";

        assertThat(client.verifyWebhookSignature(tamperedBody, signature, timestamp)).isFalse();
    }

    @Test
    void verifyWebhookSignature_wrongTimestamp_isRejected() {

        // Same body/secret, but signed with a different timestamp -- proves
        // the timestamp is genuinely part of what's signed, not decorative,
        // which is what makes this formula replay-resistant.
        String rawBody = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\",\"data\":{}}";
        String signature = base64HmacSha256("1735689600" + rawBody, CLIENT_SECRET);

        assertThat(client.verifyWebhookSignature(rawBody, signature, "1735689601")).isFalse();
    }

    @Test
    void verifyWebhookSignature_signedWithWrongSecret_isRejected() {

        String rawBody = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}";
        String timestamp = "1735689600";
        String signedWithWrongSecret = base64HmacSha256(timestamp + rawBody, "some-other-secret");

        assertThat(client.verifyWebhookSignature(rawBody, signedWithWrongSecret, timestamp)).isFalse();
    }

    @Test
    void verifyWebhookSignature_missingFields_isRejectedWithoutThrowing() {

        assertThat(client.verifyWebhookSignature(null, "sig", "123")).isFalse();
        assertThat(client.verifyWebhookSignature("{}", null, "123")).isFalse();
        assertThat(client.verifyWebhookSignature("{}", "sig", null)).isFalse();
    }

    @Test
    void verifyWebhookSignature_whenClientSecretNotConfigured_isRejected() {

        CashfreePaymentProviderClient noSecret = new CashfreePaymentProviderClient(
                "test-client-id", "", "2025-01-01", "production",
                "https://stylenestfashion.com", "https://api.stylenestfashion.com");

        String rawBody = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}";
        String timestamp = "1735689600";
        String signature = base64HmacSha256(timestamp + rawBody, CLIENT_SECRET);

        assertThat(noSecret.verifyWebhookSignature(rawBody, signature, timestamp)).isFalse();
    }

    @Test
    void parseWebhookEvent_paymentSuccess_returnsSuccessOutcome() {

        String body = """
                {"type":"PAYMENT_SUCCESS_WEBHOOK","data":{
                  "order":{"order_id":"SN-123"},
                  "payment":{"cf_payment_id":"cf_pay_1","payment_status":"SUCCESS"}
                }}
                """;

        WebhookEventResult result = client.parseWebhookEvent(body);

        assertThat(result.providerOrderId()).isEqualTo("SN-123");
        assertThat(result.outcome()).isEqualTo(PaymentOutcome.SUCCESS);
        assertThat(result.providerPaymentId()).isEqualTo("cf_pay_1");
    }

    @Test
    void parseWebhookEvent_paymentFailed_returnsFailureOutcomeWithReason() {

        String body = """
                {"type":"PAYMENT_FAILED_WEBHOOK","data":{
                  "order":{"order_id":"SN-124"},
                  "payment":{"cf_payment_id":"cf_pay_2","payment_status":"FAILED","payment_message":"Insufficient funds"}
                }}
                """;

        WebhookEventResult result = client.parseWebhookEvent(body);

        assertThat(result.providerOrderId()).isEqualTo("SN-124");
        assertThat(result.outcome()).isEqualTo(PaymentOutcome.FAILURE);
        assertThat(result.failureReason()).isEqualTo("Insufficient funds");
    }

    @Test
    void parseWebhookEvent_userDropped_isTreatedAsFailure() {

        String body = """
                {"type":"PAYMENT_USER_DROPPED_WEBHOOK","data":{
                  "order":{"order_id":"SN-125"},
                  "payment":{"payment_status":"USER_DROPPED"}
                }}
                """;

        assertThat(client.parseWebhookEvent(body).outcome()).isEqualTo(PaymentOutcome.FAILURE);
    }

    @Test
    void parseWebhookEvent_unknownEventType_isIrrelevant() {

        String body = """
                {"type":"SOME_OTHER_WEBHOOK","data":{"order":{"order_id":"SN-126"}}}
                """;

        assertThat(client.parseWebhookEvent(body).outcome()).isEqualTo(PaymentOutcome.IRRELEVANT);
    }

    @Test
    void parseWebhookEvent_malformedBody_isSafelyIrrelevant() {

        WebhookEventResult result = client.parseWebhookEvent("not json at all");

        assertThat(result.outcome()).isEqualTo(PaymentOutcome.IRRELEVANT);
        assertThat(result.providerOrderId()).isNull();
    }

    private static String base64HmacSha256(String data, String secret) {

        try {

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(bytes);

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
