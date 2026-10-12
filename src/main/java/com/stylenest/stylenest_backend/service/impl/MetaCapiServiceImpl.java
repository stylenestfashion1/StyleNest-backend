package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.OrderItem;
import com.stylenest.stylenest_backend.service.MetaCapiService;

@Service
public class MetaCapiServiceImpl implements MetaCapiService {

    private static final Logger log = LoggerFactory.getLogger(MetaCapiServiceImpl.class);

    @Value("${meta.pixel.id:2220264888551482}")
    private String pixelId;

    @Value("${meta.capi.access-token:}")
    private String accessToken;

    @Value("${meta.capi.api-version:v26.0}")
    private String apiVersion;

    @Value("${meta.capi.test-event-code:}")
    private String testEventCode;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MetaCapiServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void sendPurchaseEvent(Order order) {
        if (order == null) {
            return;
        }

        // If access token is empty or still placeholder, skip without error
        if (accessToken == null || accessToken.isBlank() || "ACCESS_TOKEN".equalsIgnoreCase(accessToken.trim())) {
            log.info("Meta CAPI: Access token not set. Skipping CAPI Purchase event for order #{}",
                    order.getOrderNumber());
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                dispatchPurchasePayload(order);
            } catch (Exception e) {
                log.warn("Meta CAPI: Failed to dispatch Purchase event for order #{}: {}",
                        order.getOrderNumber(), e.getMessage());
            }
        });
    }

    private void dispatchPurchasePayload(Order order) throws Exception {
        long eventTime = Instant.now().getEpochSecond();
        String orderNumber = order.getOrderNumber();

        // 1. User Data (Hashed for Meta Privacy)
        Map<String, Object> userData = new HashMap<>();

        String rawEmail = order.getUser() != null ? order.getUser().getEmail() : order.getGuestEmail();
        String hashedEmail = hashSha256(rawEmail);
        if (hashedEmail != null) {
            userData.put("em", List.of(hashedEmail));
        }

        String rawPhone = normalizePhone(order.getShippingPhoneCountryCode(), order.getShippingPhone());
        String hashedPhone = hashSha256(rawPhone);
        if (hashedPhone != null) {
            userData.put("ph", List.of(hashedPhone));
        }

        if (order.getShippingCity() != null && !order.getShippingCity().isBlank()) {
            String hashedCity = hashSha256(order.getShippingCity());
            if (hashedCity != null) {
                userData.put("ct", List.of(hashedCity));
            }
        }

        if (order.getShippingPostalCode() != null && !order.getShippingPostalCode().isBlank()) {
            String hashedZip = hashSha256(order.getShippingPostalCode());
            if (hashedZip != null) {
                userData.put("zp", List.of(hashedZip));
            }
        }

        if (order.getShippingCountryCode() != null && !order.getShippingCountryCode().isBlank()) {
            String hashedCountry = hashSha256(order.getShippingCountryCode());
            if (hashedCountry != null) {
                userData.put("country", List.of(hashedCountry));
            }
        }

        // 2. Custom Data (E-Commerce Purchase)
        Map<String, Object> customData = new HashMap<>();
        customData.put("currency", order.getCurrency() != null ? order.getCurrency().name() : "INR");
        customData.put("value", order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        customData.put("content_type", "product");
        customData.put("order_id", orderNumber);

        List<Map<String, Object>> contents = new ArrayList<>();
        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                Map<String, Object> itemMap = new HashMap<>();
                if (item.getProductVariant() != null && item.getProductVariant().getProduct() != null) {
                    itemMap.put("id", String.valueOf(item.getProductVariant().getProduct().getId()));
                } else {
                    itemMap.put("id", String.valueOf(item.getId()));
                }
                itemMap.put("quantity", item.getQuantity() != null ? item.getQuantity() : 1);
                itemMap.put("item_price", item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO);
                contents.add(itemMap);
            }
        }
        customData.put("contents", contents);

        // 3. Event Object
        Map<String, Object> eventObj = new HashMap<>();
        eventObj.put("event_name", "Purchase");
        eventObj.put("event_time", eventTime);
        eventObj.put("event_id", orderNumber); // Deduplication with browser Pixel
        eventObj.put("action_source", "website");
        eventObj.put("user_data", userData);
        eventObj.put("custom_data", customData);

        // 4. Root Payload
        Map<String, Object> rootPayload = new HashMap<>();
        rootPayload.put("data", List.of(eventObj));

        if (testEventCode != null && !testEventCode.isBlank()) {
            rootPayload.put("test_event_code", testEventCode.trim());
        }

        String jsonBody = objectMapper.writeValueAsString(rootPayload);

        String endpoint = String.format("https://graph.facebook.com/%s/%s/events?access_token=%s",
                apiVersion.trim(), pixelId.trim(), accessToken.trim());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Meta CAPI: Successfully sent Purchase event for order #{} (HTTP {})",
                    orderNumber, response.statusCode());
        } else {
            log.warn("Meta CAPI: Error sending Purchase event for order #{} (HTTP {}): {}",
                    orderNumber, response.statusCode(), response.body());
        }
    }

    private static String normalizePhone(String countryCode, String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        if (cleanPhone.isBlank()) {
            return null;
        }
        if (countryCode != null && !countryCode.isBlank()) {
            String cleanCode = countryCode.replaceAll("[^0-9]", "");
            if (!cleanPhone.startsWith(cleanCode)) {
                cleanPhone = cleanCode + cleanPhone;
            }
        }
        return cleanPhone;
    }

    private static String hashSha256(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
