package com.stylenest.stylenest_backend.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.exception.PaymentGatewayException;

/**
 * Thin HTTP wrapper around the three Easebuzz APIs this integration uses.
 * Endpoints and request/response shapes are taken verbatim from Easebuzz's
 * official PHP integration kit (github.com/easebuzz/paywitheasebuzz-php-lib),
 * which is the authoritative reference for the "Merchant Seamless / Merchant
 * Hosted" flow -- nothing here is guessed.
 */
@Service
public class EasebuzzClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public EasebuzzClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Value("${easebuzz.env}")
    private String env;

    private String payBaseUrl() {
        return "prod".equals(env) ? "https://pay.easebuzz.in/" : "https://testpay.easebuzz.in/";
    }

    private String dashboardBaseUrl() {
        return "prod".equals(env) ? "https://dashboard.easebuzz.in/" : "https://testdashboard.easebuzz.in/";
    }

    /**
     * POST {payBaseUrl}/payment/initiateLink -- exchanges the signed payment
     * params for a one-time access_key. Returns the access_key.
     */
    public String initiateLink(Map<String, String> params) {

        Map<String, Object> response = postForm(payBaseUrl() + "payment/initiateLink", params);

        Object status = response.get("status");
        Object data = response.get("data");

        if (!(status instanceof Number n) || n.intValue() != 1 || !(data instanceof String accessKey) || accessKey.isBlank()) {
            throw new PaymentGatewayException("Could not start the payment. Please try again.");
        }

        if (!accessKey.matches("^[a-f0-9]{64}$")) {
            throw new PaymentGatewayException("Could not start the payment. Please try again.");
        }

        return accessKey;
    }

    /**
     * POST {payBaseUrl}initiate_seamless_payment/ -- used for UPI and Net
     * Banking, where the payment mode's own fields (upi_va / bank_code) are
     * collected on our page and sent straight to Easebuzz (never routed
     * through the Easebuzz-hosted redirect page). The response is either a
     * JSON status object (e.g. UPI collect-request acknowledgement) or a raw
     * HTML bank/3DS authentication page as a plain string.
     */
    public SeamlessResult initiateSeamlessPayment(Map<String, String> params) {

        String url = payBaseUrl() + "initiate_seamless_payment/";

        String raw;

        try {

            raw = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(toMultiValueMap(params))
                    .retrieve()
                    .body(String.class);

        } catch (RestClientException e) {

            throw new PaymentGatewayException("Could not reach the payment gateway. Please try again.");
        }

        if (raw == null || raw.isBlank()) {
            throw new PaymentGatewayException("The payment gateway returned an empty response.");
        }

        String trimmed = raw.trim();

        if (trimmed.regionMatches(true, 0, "<!DOCTYPE", 0, 9) || trimmed.toLowerCase().contains("<html")) {
            return SeamlessResult.html(trimmed);
        }

        try {

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(trimmed, Map.class);

            return SeamlessResult.json(parsed);

        } catch (Exception e) {

            throw new PaymentGatewayException("The payment gateway returned an unexpected response.");
        }
    }

    /**
     * POST {dashboardBaseUrl}transaction/v2/retrieve -- authoritative,
     * server-side status check for a txnid. Used as a defense-in-depth
     * double check before trusting a SURL/FURL callback's own status field.
     */
    public Map<String, Object> retrieveTransaction(String txnid, String key, String hash) {

        Map<String, String> params = new HashMap<>();
        params.put("txnid", txnid);
        params.put("key", key);
        params.put("hash", hash);

        return postForm(dashboardBaseUrl() + "transaction/v2/retrieve", params);
    }

    private Map<String, Object> postForm(String url, Map<String, String> params) {

        try {

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(toMultiValueMap(params))
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new PaymentGatewayException("The payment gateway returned an empty response.");
            }

            return response;

        } catch (PaymentGatewayException e) {

            throw e;

        } catch (RestClientException e) {

            throw new PaymentGatewayException("Could not reach the payment gateway. Please try again.");
        }
    }

    private MultiValueMap<String, String> toMultiValueMap(Map<String, String> params) {

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();

        params.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                body.add(key, value);
            }
        });

        return body;
    }

    public record SeamlessResult(boolean html, String htmlContent, Map<String, Object> json) {

        public static SeamlessResult html(String content) {
            return new SeamlessResult(true, content, null);
        }

        public static SeamlessResult json(Map<String, Object> content) {
            return new SeamlessResult(false, null, content);
        }
    }
}
