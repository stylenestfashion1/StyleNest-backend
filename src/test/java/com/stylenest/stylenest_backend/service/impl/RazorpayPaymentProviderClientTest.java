package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.exception.PaymentGatewayException;

/**
 * Exercises the actual cryptographic verification logic (HMAC-SHA256, per
 * Razorpay's own documented formulas) with no mocking and no network call
 * -- genuinely testable without real Razorpay credentials, unlike opening
 * a live Razorpay Checkout session. The signatures here are computed
 * independently of RazorpayPaymentProviderClient (plain javax.crypto), so
 * this doesn't just assert the production code agrees with itself -- it
 * proves the SDK's Utils.verifyPaymentSignature/verifyWebhookSignature
 * (which the production code delegates to) implement the documented
 * formula correctly against a real secret, and that a tampered signature
 * or a payload signed with the wrong secret is correctly rejected.
 */
class RazorpayPaymentProviderClientTest {

    private static final String KEY_SECRET = "test-key-secret";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    private final RazorpayPaymentProviderClient client =
            new RazorpayPaymentProviderClient("test-key-id", KEY_SECRET, WEBHOOK_SECRET);

    @Test
    void publicKeyId_returnsConfiguredKeyId() {
        assertThat(client.publicKeyId()).isEqualTo("test-key-id");
    }

    @Test
    void createOrder_whenGatewayNotConfigured_failsCleanlyWithoutAnyNetworkCall() {

        RazorpayPaymentProviderClient unconfigured = new RazorpayPaymentProviderClient("", "", "");

        assertThatThrownBy(() -> unconfigured.createOrder(BigDecimal.TEN, Currency.INR, "SN-1"))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void verifyPaymentSignature_genuineHmac_isAccepted() {

        String orderId = "order_abc123";
        String paymentId = "pay_xyz789";
        String signature = hmacSha256Hex(orderId + "|" + paymentId, KEY_SECRET);

        assertThat(client.verifyPaymentSignature(orderId, paymentId, signature)).isTrue();
    }

    @Test
    void verifyPaymentSignature_tamperedSignature_isRejected() {

        String orderId = "order_abc123";
        String paymentId = "pay_xyz789";
        String genuine = hmacSha256Hex(orderId + "|" + paymentId, KEY_SECRET);
        String tampered = genuine.substring(0, genuine.length() - 1) + (genuine.endsWith("0") ? "1" : "0");

        assertThat(client.verifyPaymentSignature(orderId, paymentId, tampered)).isFalse();
    }

    @Test
    void verifyPaymentSignature_signedWithWrongSecret_isRejected() {

        String orderId = "order_abc123";
        String paymentId = "pay_xyz789";
        String signedWithWrongSecret = hmacSha256Hex(orderId + "|" + paymentId, "some-other-secret");

        assertThat(client.verifyPaymentSignature(orderId, paymentId, signedWithWrongSecret)).isFalse();
    }

    @Test
    void verifyPaymentSignature_missingFields_isRejectedWithoutThrowing() {

        assertThat(client.verifyPaymentSignature(null, "pay_xyz789", "sig")).isFalse();
        assertThat(client.verifyPaymentSignature("order_abc123", null, "sig")).isFalse();
        assertThat(client.verifyPaymentSignature("order_abc123", "pay_xyz789", null)).isFalse();
    }

    @Test
    void verifyWebhookSignature_genuineHmacOverRawBody_isAccepted() {

        String rawBody = "{\"event\":\"payment.captured\",\"payload\":{}}";
        String signature = hmacSha256Hex(rawBody, WEBHOOK_SECRET);

        assertThat(client.verifyWebhookSignature(rawBody, signature)).isTrue();
    }

    @Test
    void verifyWebhookSignature_bodyModifiedAfterSigning_isRejected() {

        String originalBody = "{\"event\":\"payment.captured\",\"payload\":{}}";
        String signature = hmacSha256Hex(originalBody, WEBHOOK_SECRET);

        // Simulates a man-in-the-middle or logging/proxy layer altering the
        // body after Razorpay signed it -- the signature must not validate
        // against a different body, which is exactly why verification must
        // run against the raw, unparsed bytes rather than a re-serialized
        // JSON tree.
        String tamperedBody = "{\"event\":\"payment.captured\",\"payload\":{\"tampered\":true}}";

        assertThat(client.verifyWebhookSignature(tamperedBody, signature)).isFalse();
    }

    @Test
    void verifyWebhookSignature_whenWebhookSecretNotConfigured_isRejected() {

        RazorpayPaymentProviderClient noWebhookSecret =
                new RazorpayPaymentProviderClient("test-key-id", KEY_SECRET, "");

        String rawBody = "{\"event\":\"payment.captured\"}";
        String signature = hmacSha256Hex(rawBody, WEBHOOK_SECRET);

        assertThat(noWebhookSecret.verifyWebhookSignature(rawBody, signature)).isFalse();
    }

    private static String hmacSha256Hex(String data, String secret) {

        try {

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
