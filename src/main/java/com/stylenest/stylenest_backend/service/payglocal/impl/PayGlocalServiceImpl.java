package com.stylenest.stylenest_backend.service.payglocal.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.config.PayGlocalProperties;
import com.stylenest.stylenest_backend.dto.bulk.payment.PayGlocalInitiateResponse;
import com.stylenest.stylenest_backend.entity.BulkOrder;
import com.stylenest.stylenest_backend.entity.QuickPayment;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.InvalidPaymentSignatureException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.repository.BulkOrderRepository;
import com.stylenest.stylenest_backend.repository.QuickPaymentRepository;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.InvoiceGenerationService;
import com.stylenest.stylenest_backend.service.InvoiceService;
import com.stylenest.stylenest_backend.service.payglocal.PayGlocalCryptoService;
import com.stylenest.stylenest_backend.service.payglocal.PayGlocalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PayGlocalServiceImpl implements PayGlocalService {

    private final PayGlocalProperties properties;
    private final PayGlocalCryptoService cryptoService;
    private final BulkOrderRepository bulkOrderRepository;
    private final com.stylenest.stylenest_backend.repository.BulkProductRepository bulkProductRepository;
    private final ObjectMapper objectMapper;
    private final InvoiceGenerationService invoiceGenerationService;
    private final InvoiceService invoiceService;
    private final EmailService emailService;
    private final RestClient restClient;
    private final QuickPaymentRepository quickPaymentRepository;

    @Override
    @Transactional
    public PayGlocalInitiateResponse initiatePayment(String bulkOrderNumber) {
        BulkOrder order = bulkOrderRepository.findByBulkOrderNumber(bulkOrderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Bulk order not found: " + bulkOrderNumber));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BadRequestException("Order is already paid.");
        }

        // Split customer name into first & last name
        String name = order.getCustomerName().trim();
        String firstName = name;
        String lastName = "Customer";
        int lastSpace = name.lastIndexOf(' ');
        if (lastSpace > 0) {
            firstName = name.substring(0, lastSpace);
            lastName = name.substring(lastSpace + 1);
        }

        // Build Payload according to PayGlocal's specification
        Map<String, Object> billingData = new HashMap<>();
        billingData.put("firstName", firstName);
        billingData.put("lastName", lastName);
        billingData.put("addressStreet1", order.getShippingAddressLine1());
        billingData.put("addressStreet2", order.getShippingAddressLine2() != null ? order.getShippingAddressLine2() : "");
        billingData.put("addressCity", order.getShippingCity());
        billingData.put("addressState", order.getShippingState());
        billingData.put("addressPostalCode", order.getShippingPostalCode());
        String countryCode = resolveCountryCode(order.getShippingCountry());
        Map.Entry<String, String> phoneInfo = resolvePhoneAndCallingCode(order.getCustomerPhone(), countryCode);

        billingData.put("addressCountry", countryCode);
        billingData.put("emailId", order.getCustomerEmail());
        billingData.put("callingCode", phoneInfo.getKey());
        billingData.put("phoneNumber", phoneInfo.getValue());

        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("totalAmount", order.getTotalAmount().toPlainString());
        paymentData.put("txnCurrency", "INR");
        paymentData.put("billingData", billingData);

        Map<String, Object> payload = new HashMap<>();
        payload.put("merchantTxnId", order.getBulkOrderNumber());
        payload.put("paymentData", paymentData);
        payload.put("merchantCallbackURL", properties.getCallbackUrl());

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            log.info("Initiating PayGlocal payment for order {}. Amount: INR {}",
                    order.getBulkOrderNumber(), order.getTotalAmount());

            // 1. JWE Encrypt
            String jweToken = cryptoService.encryptPayload(jsonPayload);

            // 2. JWS Sign
            String jwsToken = cryptoService.signPayload(jweToken);

            // 3. Call PayGlocal API: POST /gl/v1/payments/initiate/paycollect
            String url = properties.getBaseUrl() + "/gl/v1/payments/initiate/paycollect";

            String responseString = restClient.post()
                    .uri(url)
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("x-gl-token-external", jwsToken)
                    .body(jweToken)
                    .retrieve()
                    .body(String.class);

            log.info("PayGlocal API Response for {}: {}", order.getBulkOrderNumber(), responseString);

            Map<String, Object> respMap = objectMapper.readValue(responseString, new TypeReference<Map<String, Object>>() {});

            String gid = (String) respMap.get("gid");
            String status = (String) respMap.get("status");
            String redirectUrl = null;

            if (respMap.containsKey("data") && respMap.get("data") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataObj = (Map<String, Object>) respMap.get("data");
                redirectUrl = (String) dataObj.get("redirectUrl");
            }

            if (redirectUrl == null) {
                throw new BadRequestException("Failed to obtain redirect URL from PayGlocal: " + responseString);
            }

            // Save tracking info on BulkOrder
            order.setPaymentMethod(PaymentMethod.ONLINE);
            order.setPayglocalGid(gid);
            order.setPayglocalTxnId(order.getBulkOrderNumber());
            bulkOrderRepository.save(order);

            return PayGlocalInitiateResponse.builder()
                    .bulkOrderNumber(order.getBulkOrderNumber())
                    .redirectUrl(redirectUrl)
                    .gid(gid)
                    .status(status != null ? status : "INITIATED")
                    .build();

        } catch (RestClientResponseException e) {
            log.error("PayGlocal API HTTP Error for {}: {} - {}",
                    order.getBulkOrderNumber(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("PayGlocal gateway error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("PayGlocal payment initiation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PayGlocal payment error: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public String handleCallback(Map<String, String> formData) {
        log.info("PayGlocal callback received: {}", formData);

        String jwsToken = formData != null ? formData.get("x-gl-token") : null;
        if (jwsToken == null || jwsToken.isBlank()) {
            log.warn("PayGlocal callback rejected: Missing x-gl-token cryptographic signature");
            return "https://stylenestfashion.com/bulk-orders/cart?error=missing_signature";
        }

        // Verify cryptographic signature against PayGlocal public key
        if (!cryptoService.verifyJwsToken(jwsToken)) {
            log.error("PayGlocal callback signature verification failed!");
            return "https://stylenestfashion.com/bulk-orders/cart?error=invalid_signature";
        }

        Map<String, Object> claims = cryptoService.parseJwsClaims(jwsToken);
        log.info("PayGlocal callback verified claims: {}", claims);

        String merchantTxnId = (String) claims.get("merchantTxnId");
        if (merchantTxnId == null) {
            merchantTxnId = (String) claims.get("merchantUniqueId");
        }
        String status = (String) claims.get("status");
        String gid = (String) claims.get("gid");

        if (merchantTxnId == null) {
            return "https://stylenestfashion.com/bulk-orders/cart?error=missing_order";
        }

        BulkOrder order = bulkOrderRepository.findByBulkOrderNumber(merchantTxnId).orElse(null);
        if (order == null) {
            log.error("Order not found in PayGlocal callback: {}", merchantTxnId);
            return "https://stylenestfashion.com/bulk-orders/cart?error=order_not_found";
        }

        // Idempotency: if order is already marked PAID, return success redirect immediately
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Bulk order {} already marked as PAID. Returning confirmation redirect.", order.getBulkOrderNumber());
            return "https://stylenestfashion.com/bulk-orders/order-confirmation?orderNumber="
                    + order.getBulkOrderNumber() + "&status=success";
        }

        // Verify amount and currency if present in claims
        try {
            validateAmountAndCurrency(claims, order.getTotalAmount(), "INR");
        } catch (Exception e) {
            log.error("PayGlocal callback amount/currency mismatch for order {}: {}", merchantTxnId, e.getMessage());
            return "https://stylenestfashion.com/bulk-orders/cart?error=payment_mismatch";
        }

        boolean isSuccess = "SENT_FOR_CAPTURE".equalsIgnoreCase(status)
                || "COMPLETED".equalsIgnoreCase(status)
                || "SUCCESS".equalsIgnoreCase(status);

        if (isSuccess) {
            confirmOrderPaid(order, gid);
            return "https://stylenestfashion.com/bulk-orders/order-confirmation?orderNumber="
                    + order.getBulkOrderNumber() + "&status=success";
        } else {
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.FAILED);
            restoreStockIfCancelled(order);
            bulkOrderRepository.save(order);
            return "https://stylenestfashion.com/bulk-orders/order-confirmation?orderNumber="
                    + order.getBulkOrderNumber() + "&status=failed";
        }
    }

    private void restoreStockIfCancelled(BulkOrder order) {
        if (order.getItems() != null) {
            for (var item : order.getItems()) {
                if (item.getBulkProduct() != null && item.getBulkProduct().getAvailableStock() != null) {
                    var prod = item.getBulkProduct();
                    prod.setAvailableStock(prod.getAvailableStock() + item.getQuantity());
                    bulkProductRepository.save(prod);
                }
            }
        }
    }

    @Override
    @Transactional
    public void handleWebhook(String rawPayload, String jwsHeader) {
        log.info("PayGlocal webhook received. Header present: {}, Payload present: {}",
                jwsHeader != null && !jwsHeader.isBlank(),
                rawPayload != null && !rawPayload.isBlank());

        // 1. Identify token from x-gl-token header or compact JWS payload body
        String token = (jwsHeader != null && !jwsHeader.isBlank()) ? jwsHeader.trim() : null;
        if (token == null && rawPayload != null && !rawPayload.isBlank()) {
            String trimmed = rawPayload.trim();
            // JWS compact serialization format: BASE64URL(Header) + '.' + BASE64URL(Payload) + '.' + BASE64URL(Signature)
            if (trimmed.split("\\.").length == 3 && !trimmed.startsWith("{")) {
                token = trimmed;
            }
        }

        if (token == null) {
            log.warn("PayGlocal webhook rejected: Missing JWS signature token");
            throw new InvalidPaymentSignatureException("PayGlocal webhook signature verification failed: Missing signature token");
        }

        // 2. Cryptographic signature check against PayGlocal RSA public key
        if (!cryptoService.verifyJwsToken(token)) {
            log.error("PayGlocal webhook signature verification failed!");
            throw new InvalidPaymentSignatureException("PayGlocal webhook signature verification failed: Invalid signature");
        }

        // 3. Parse claims from verified JWS token (never from raw unverified JSON body)
        Map<String, Object> claims = cryptoService.parseJwsClaims(token);
        if (claims.isEmpty()) {
            log.warn("PayGlocal webhook rejected: Empty JWS claims");
            throw new InvalidPaymentSignatureException("PayGlocal webhook signature verification failed: Empty claims");
        }

        String merchantTxnId = (String) claims.get("merchantTxnId");
        if (merchantTxnId == null) {
            merchantTxnId = (String) claims.get("merchantUniqueId");
        }
        if (merchantTxnId == null) {
            throw new BadRequestException("Missing merchantTxnId in PayGlocal webhook payload");
        }
        final String orderTxnId = merchantTxnId;

        String status = (String) claims.get("status");
        String gid = (String) claims.get("gid");

        // Handle QuickPayment if merchantTxnId starts with QP-
        if (orderTxnId.startsWith("QP-")) {
            processQuickPaymentWebhook(orderTxnId, status, gid, claims);
            return;
        }

        // Handle BulkOrder
        BulkOrder order = bulkOrderRepository.findByBulkOrderNumber(orderTxnId)
                .orElseThrow(() -> new ResourceNotFoundException("Bulk order not found: " + orderTxnId));

        // 4. Idempotency check: if order is already marked PAID, return safely without re-processing
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Bulk order {} already marked as PAID. Webhook processed idempotently.", order.getBulkOrderNumber());
            return;
        }

        // 5. Amount & Currency matching
        validateAmountAndCurrency(claims, order.getTotalAmount(), "INR");

        // 6. Apply payment status
        boolean isSuccess = "SENT_FOR_CAPTURE".equalsIgnoreCase(status)
                || "COMPLETED".equalsIgnoreCase(status)
                || "SUCCESS".equalsIgnoreCase(status);

        if (isSuccess) {
            confirmOrderPaid(order, gid);
        } else if ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.FAILED);
            restoreStockIfCancelled(order);
            bulkOrderRepository.save(order);
            log.info("Bulk order {} marked as FAILED/CANCELLED via PayGlocal webhook (Status: {})", order.getBulkOrderNumber(), status);
        } else {
            log.info("Bulk order {} received non-terminal PayGlocal status: {}", order.getBulkOrderNumber(), status);
        }
    }

    private void processQuickPaymentWebhook(String paymentNumber, String status, String gid, Map<String, Object> claims) {
        QuickPayment payment = quickPaymentRepository.findByPaymentNumber(paymentNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Quick payment not found: " + paymentNumber));

        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Quick payment {} already marked as PAID. Webhook processed idempotently.", paymentNumber);
            return;
        }

        validateAmountAndCurrency(claims, payment.getAmount(), payment.getCurrency());

        boolean isSuccess = "SENT_FOR_CAPTURE".equalsIgnoreCase(status)
                || "COMPLETED".equalsIgnoreCase(status)
                || "SUCCESS".equalsIgnoreCase(status);

        if (isSuccess) {
            payment.setPaymentStatus(PaymentStatus.PAID);
            if (gid != null) {
                payment.setPayglocalGid(gid);
            }
            quickPaymentRepository.save(payment);
            log.info("Quick payment {} confirmed as PAID via PayGlocal webhook (GID: {})", paymentNumber, gid);
            try {
                emailService.sendQuickPaymentReceiptEmail(
                        payment.getCustomerEmail(),
                        payment.getCustomerName(),
                        payment.getPaymentNumber(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getNote()
                );
            } catch (Exception e) {
                log.error("Failed to send Quick Payment receipt email for {}: {}", paymentNumber, e.getMessage());
            }
        } else if ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            quickPaymentRepository.save(payment);
            log.info("Quick payment {} marked as FAILED via PayGlocal webhook (Status: {})", paymentNumber, status);
        }
    }

    private void validateAmountAndCurrency(Map<String, Object> claims, BigDecimal expectedAmount, String expectedCurrency) {
        if (expectedAmount == null) {
            return;
        }
        Object paymentDataObj = claims.get("paymentData");
        BigDecimal claimAmount = null;
        String claimCurrency = null;

        if (paymentDataObj instanceof Map<?, ?> pd) {
            Object amt = pd.get("totalAmount");
            if (amt == null) amt = pd.get("amount");
            if (amt != null) {
                try {
                    claimAmount = new BigDecimal(amt.toString().trim());
                } catch (Exception ignored) {}
            }
            Object curr = pd.get("txnCurrency");
            if (curr == null) curr = pd.get("currency");
            if (curr != null) {
                claimCurrency = curr.toString().trim();
            }
        } else {
            Object amt = claims.get("totalAmount");
            if (amt == null) amt = claims.get("amount");
            if (amt != null) {
                try {
                    claimAmount = new BigDecimal(amt.toString().trim());
                } catch (Exception ignored) {}
            }
            Object curr = claims.get("txnCurrency");
            if (curr == null) curr = claims.get("currency");
            if (curr != null) {
                claimCurrency = curr.toString().trim();
            }
        }

        if (claimAmount != null && claimAmount.compareTo(expectedAmount) != 0) {
            log.error("PayGlocal amount mismatch: expected {}, received in claims {}", expectedAmount, claimAmount);
            throw new BadRequestException("Payment amount mismatch: expected " + expectedAmount + ", got " + claimAmount);
        }

        if (claimCurrency != null && expectedCurrency != null && !claimCurrency.equalsIgnoreCase(expectedCurrency)) {
            log.error("PayGlocal currency mismatch: expected {}, received in claims {}", expectedCurrency, claimCurrency);
            throw new BadRequestException("Payment currency mismatch: expected " + expectedCurrency + ", got " + claimCurrency);
        }
    }

    private void confirmOrderPaid(BulkOrder order, String gid) {
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        if (gid != null) {
            order.setPayglocalGid(gid);
        }
        bulkOrderRepository.save(order);
        log.info("Bulk order {} successfully confirmed as PAID via PayGlocal (GID: {})",
                order.getBulkOrderNumber(), gid);

        sendInvoiceEmail(order);
    }

    private void sendInvoiceEmail(BulkOrder order) {
        try {
            var invoice = invoiceGenerationService.generateForBulkOrder(order);
            byte[] pdf = invoiceService.generatePdf(invoice);

            emailService.sendInvoiceEmail(
                    order.getCustomerEmail(),
                    order.getCustomerName(),
                    invoice.getInvoiceNumber(),
                    order.getBulkOrderNumber(),
                    order.getTotalAmount(),
                    pdf
            );
            log.info("Sent wholesale invoice email for bulk order {}", order.getBulkOrderNumber());
        } catch (Exception ex) {
            log.error("Failed to generate/send invoice email for bulk order {}: {}",
                    order.getBulkOrderNumber(), ex.getMessage());
        }
    }

    private String resolveCountryCode(String country) {
        if (country == null || country.isBlank()) {
            return "IN";
        }
        String trimmed = country.trim().toUpperCase();
        if (trimmed.length() == 2) {
            return trimmed;
        }
        if (trimmed.equals("USA") || trimmed.equals("UNITED STATES") || trimmed.equals("UNITED STATES OF AMERICA")) {
            return "US";
        }
        if (trimmed.equals("UK") || trimmed.equals("UNITED KINGDOM") || trimmed.equals("GREAT BRITAIN") || trimmed.equals("ENGLAND")) {
            return "GB";
        }
        if (trimmed.equals("UAE") || trimmed.equals("UNITED ARAB EMIRATES") || trimmed.equals("DUBAI")) {
            return "AE";
        }
        if (trimmed.equals("CANADA") || trimmed.equals("CAN")) {
            return "CA";
        }
        if (trimmed.equals("AUSTRALIA") || trimmed.equals("AUS")) {
            return "AU";
        }
        if (trimmed.equals("SINGAPORE") || trimmed.equals("SGP")) {
            return "SG";
        }
        if (trimmed.equals("INDIA") || trimmed.equals("IND") || trimmed.equals("BHARAT")) {
            return "IN";
        }
        for (String iso : Locale.getISOCountries()) {
            Locale locale = new Locale("", iso);
            if (locale.getDisplayCountry().equalsIgnoreCase(trimmed) || locale.getISO3Country().equalsIgnoreCase(trimmed)) {
                return iso;
            }
        }
        return "IN";
    }

    private Map.Entry<String, String> resolvePhoneAndCallingCode(String rawPhone, String countryCode) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return Map.entry("+91", "");
        }
        String cleaned = rawPhone.replaceAll("[^0-9+]", "");
        if (cleaned.startsWith("+")) {
            if (cleaned.startsWith("+91") && cleaned.length() > 3) {
                return Map.entry("+91", cleaned.substring(3));
            } else if (cleaned.startsWith("+1") && cleaned.length() > 2) {
                return Map.entry("+1", cleaned.substring(2));
            } else if (cleaned.startsWith("+44") && cleaned.length() > 3) {
                return Map.entry("+44", cleaned.substring(3));
            } else if (cleaned.startsWith("+971") && cleaned.length() > 4) {
                return Map.entry("+971", cleaned.substring(4));
            } else if (cleaned.startsWith("+61") && cleaned.length() > 3) {
                return Map.entry("+61", cleaned.substring(3));
            } else if (cleaned.startsWith("+65") && cleaned.length() > 3) {
                return Map.entry("+65", cleaned.substring(3));
            } else if (cleaned.startsWith("+49") && cleaned.length() > 3) {
                return Map.entry("+49", cleaned.substring(3));
            } else if (cleaned.startsWith("+33") && cleaned.length() > 3) {
                return Map.entry("+33", cleaned.substring(3));
            }
        }
        String defaultCallingCode = switch (countryCode) {
            case "US", "CA" -> "+1";
            case "GB" -> "+44";
            case "AE" -> "+971";
            case "AU" -> "+61";
            case "SG" -> "+65";
            case "DE" -> "+49";
            case "FR" -> "+33";
            case "SA" -> "+966";
            default -> "+91";
        };
        String digitsOnly = cleaned.replaceAll("[^0-9]", "");
        return Map.entry(defaultCallingCode, digitsOnly);
    }
}
