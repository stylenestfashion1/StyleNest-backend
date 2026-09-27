package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.exception.PaymentGatewayException;
import com.stylenest.stylenest_backend.service.PaymentProviderClient;

/**
 * The only class in this codebase that talks to the Razorpay Java SDK
 * directly -- everything else (PaymentServiceImpl, OrderServiceImpl) goes
 * through the PaymentProviderClient interface. Request/response shapes
 * and the signature-verification formulas here are exactly what
 * Razorpay's official docs and the razorpay-java SDK itself document/
 * implement (see Utils.verifyPaymentSignature / verifyWebhookSignature);
 * nothing here is guessed.
 */
@Service
public class RazorpayPaymentProviderClient implements PaymentProviderClient {

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    // Built lazily (not eagerly at startup) so a StyleNest environment
    // with no Razorpay keys configured yet (e.g. a fresh local/test setup)
    // can still start up cleanly -- the "not configured" case is only
    // ever surfaced when a payment is actually attempted, exactly like
    // the old Easebuzz integration's requireGatewayConfigured() did.
    private RazorpayClient client;

    public RazorpayPaymentProviderClient(
            @Value("${razorpay.key-id:}") String keyId,
            @Value("${razorpay.key-secret:}") String keySecret,
            @Value("${razorpay.webhook-secret:}") String webhookSecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
    }

    private synchronized RazorpayClient client() {

        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new PaymentGatewayException(
                    "Online payments are not configured yet. Please use Cash on Delivery for now.");
        }

        if (client == null) {
            try {
                client = new RazorpayClient(keyId, keySecret);
            } catch (RazorpayException e) {
                throw new PaymentGatewayException("Could not reach the payment gateway. Please try again.");
            }
        }

        return client;
    }

    @Override
    public String publicKeyId() {
        return keyId;
    }

    @Override
    public ProviderOrder createOrder(BigDecimal amount, Currency currency, String receipt) {

        try {

            JSONObject request = new JSONObject();
            request.put("amount", toMinorUnits(amount));
            request.put("currency", currency.name());
            request.put("receipt", receipt);

            Order order = client().orders.create(request);

            return new ProviderOrder(order.get("id"), order.get("status"));

        } catch (RazorpayException e) {

            throw new PaymentGatewayException("Could not start the payment. Please try again.");
        }
    }

    @Override
    public boolean verifyPaymentSignature(String providerOrderId, String providerPaymentId, String signature) {

        if (isBlank(providerOrderId) || isBlank(providerPaymentId) || isBlank(signature)) {
            return false;
        }

        try {

            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", providerOrderId);
            attributes.put("razorpay_payment_id", providerPaymentId);
            attributes.put("razorpay_signature", signature);

            return Utils.verifyPaymentSignature(attributes, keySecret);

        } catch (RazorpayException e) {

            // A malformed/forged signature throwing during verification is
            // just as much "not verified" as one that verifies to false.
            return false;
        }
    }

    @Override
    public ProviderPayment fetchPayment(String providerPaymentId) {

        try {

            Payment payment = client().payments.fetch(providerPaymentId);

            return toProviderPayment(payment);

        } catch (RazorpayException e) {

            throw new PaymentGatewayException("Could not verify the payment. Please try again.");
        }
    }

    @Override
    public ProviderPayment capturePayment(String providerPaymentId, BigDecimal amount, Currency currency) {

        try {

            JSONObject request = new JSONObject();
            request.put("amount", toMinorUnits(amount));
            request.put("currency", currency.name());

            Payment payment = client().payments.capture(providerPaymentId, request);

            return toProviderPayment(payment);

        } catch (RazorpayException e) {

            throw new PaymentGatewayException("Could not capture the payment. Please try again.");
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {

        if (webhookSecret.isBlank() || isBlank(rawBody) || isBlank(signatureHeader)) {
            return false;
        }

        try {

            return Utils.verifyWebhookSignature(rawBody, signatureHeader, webhookSecret);

        } catch (RazorpayException e) {

            return false;
        }
    }

    private ProviderPayment toProviderPayment(Payment payment) {

        String errorReason = payment.has("error_description") ? payment.get("error_description") : null;
        String method = payment.has("method") ? payment.get("method") : null;

        return new ProviderPayment(
                payment.get("id"),
                payment.get("order_id"),
                payment.get("status"),
                method,
                errorReason);
    }

    /** Razorpay amounts are always the smallest currency subunit (paise for INR, cents for USD) -- both are 2 decimal places, never floating point. */
    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
