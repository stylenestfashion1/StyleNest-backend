package com.stylenest.stylenest_backend.service.impl;

import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.config.PayGlocalProperties;
import com.stylenest.stylenest_backend.dto.quickpay.QuickPaymentInitiateRequest;
import com.stylenest.stylenest_backend.dto.quickpay.QuickPaymentResponse;
import com.stylenest.stylenest_backend.entity.QuickPayment;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.repository.QuickPaymentRepository;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.QuickPaymentService;
import com.stylenest.stylenest_backend.service.payglocal.PayGlocalCryptoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuickPaymentServiceImpl implements QuickPaymentService {

    private final PayGlocalProperties properties;
    private final PayGlocalCryptoService cryptoService;
    private final QuickPaymentRepository quickPaymentRepository;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final RestClient restClient;

    private static final String QUICK_PAY_CALLBACK_URL = "https://api.stylenestfashion.com/api/quick-pay/callback";

    @Override
    @Transactional
    public QuickPaymentResponse initiateQuickPayment(QuickPaymentInitiateRequest request) {
        // Validate Currency
        String currency = "INR";
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            String upper = request.getCurrency().trim().toUpperCase();
            if (upper.equals("USD") || upper.equals("INR")) {
                currency = upper;
            }
        }

        // Generate unique payment number: QP-timestamp-4digits
        String paymentNumber = "QP-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);

        // Sanitize and prepare address fields with sensible defaults for PayGlocal requirement
        String addressLine1 = (request.getAddressLine1() != null && !request.getAddressLine1().isBlank())
                ? request.getAddressLine1().trim() : "StyleNest Fashion Direct Order";
        String city = (request.getCity() != null && !request.getCity().isBlank())
                ? request.getCity().trim() : "Mumbai";
        String state = (request.getState() != null && !request.getState().isBlank())
                ? request.getState().trim() : "Maharashtra";
        String postalCode = (request.getPostalCode() != null && !request.getPostalCode().isBlank())
                ? request.getPostalCode().trim() : "400001";
        String country = (request.getCountry() != null && !request.getCountry().isBlank())
                ? request.getCountry().trim() : (currency.equals("USD") ? "United States" : "India");

        QuickPayment payment = QuickPayment.builder()
                .paymentNumber(paymentNumber)
                .customerName(request.getCustomerName().trim())
                .customerEmail(request.getCustomerEmail().trim().toLowerCase())
                .customerPhone(request.getCustomerPhone().trim())
                .addressLine1(addressLine1)
                .addressLine2(request.getAddressLine2())
                .city(city)
                .state(state)
                .postalCode(postalCode)
                .country(country)
                .amount(request.getAmount().setScale(2, RoundingMode.HALF_UP))
                .currency(currency)
                .note(request.getNote())
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        payment = quickPaymentRepository.save(payment);

        // Name parsing
        String name = payment.getCustomerName();
        String firstName = name;
        String lastName = "Customer";
        int lastSpace = name.lastIndexOf(' ');
        if (lastSpace > 0) {
            firstName = name.substring(0, lastSpace);
            lastName = name.substring(lastSpace + 1);
        }

        String countryCode = resolveCountryCode(payment.getCountry());
        Map.Entry<String, String> phoneInfo = resolvePhoneAndCallingCode(payment.getCustomerPhone(), countryCode);

        // PayGlocal Payload
        Map<String, Object> billingData = new HashMap<>();
        billingData.put("firstName", firstName);
        billingData.put("lastName", lastName);
        billingData.put("addressStreet1", payment.getAddressLine1());
        billingData.put("addressStreet2", payment.getAddressLine2() != null ? payment.getAddressLine2() : "");
        billingData.put("addressCity", payment.getCity());
        billingData.put("addressState", payment.getState());
        billingData.put("addressPostalCode", payment.getPostalCode());
        billingData.put("addressCountry", countryCode);
        billingData.put("emailId", payment.getCustomerEmail());
        billingData.put("callingCode", phoneInfo.getKey());
        billingData.put("phoneNumber", phoneInfo.getValue());

        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("totalAmount", payment.getAmount().toPlainString());
        paymentData.put("txnCurrency", payment.getCurrency());
        paymentData.put("billingData", billingData);

        Map<String, Object> payload = new HashMap<>();
        payload.put("merchantTxnId", payment.getPaymentNumber());
        payload.put("paymentData", paymentData);
        payload.put("merchantCallbackURL", QUICK_PAY_CALLBACK_URL);

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            log.info("Initiating PayGlocal Quick Payment {} for {} {}. Customer: {}",
                    paymentNumber, currency, payment.getAmount(), payment.getCustomerEmail());

            // 1. JWE Encrypt
            String jweToken = cryptoService.encryptPayload(jsonPayload);

            // 2. JWS Sign
            String jwsToken = cryptoService.signPayload(jweToken);

            // 3. Post to PayGlocal
            String url = properties.getBaseUrl() + "/gl/v1/payments/initiate/paycollect";
            String responseString = restClient.post()
                    .uri(url)
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("x-gl-token-external", jwsToken)
                    .body(jweToken)
                    .retrieve()
                    .body(String.class);

            log.info("PayGlocal Quick Payment API Response for {}: {}", paymentNumber, responseString);

            Map<String, Object> respMap = objectMapper.readValue(responseString, new TypeReference<Map<String, Object>>() {});
            String gid = (String) respMap.get("gid");
            String redirectUrl = null;

            if (respMap.containsKey("data") && respMap.get("data") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataObj = (Map<String, Object>) respMap.get("data");
                redirectUrl = (String) dataObj.get("redirectUrl");
            }

            if (redirectUrl == null) {
                throw new BadRequestException("Failed to obtain redirect URL from PayGlocal: " + responseString);
            }

            payment.setPayglocalGid(gid);
            payment.setPayglocalTxnId(paymentNumber);
            quickPaymentRepository.save(payment);

            return mapToResponse(payment, redirectUrl);

        } catch (RestClientResponseException e) {
            log.error("PayGlocal API HTTP Error for Quick Payment {}: {} - {}",
                    paymentNumber, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("PayGlocal gateway error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("PayGlocal quick payment initiation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PayGlocal payment error: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public String handleCallback(Map<String, String> formData) {
        log.info("PayGlocal quick payment callback received: {}", formData);

        String jwsToken = formData.get("x-gl-token");
        String merchantTxnId = formData.get("merchantTxnId");
        String status = formData.get("status");
        String gid = formData.get("gid");

        if (jwsToken != null && !jwsToken.isBlank()) {
            boolean valid = cryptoService.verifyJwsToken(jwsToken);
            if (!valid) {
                log.warn("PayGlocal Quick Payment callback signature verification failed!");
            }
            Map<String, Object> claims = cryptoService.parseJwsClaims(jwsToken);
            log.info("PayGlocal Quick Payment callback claims: {}", claims);

            if (claims.containsKey("merchantTxnId")) {
                merchantTxnId = (String) claims.get("merchantTxnId");
            } else if (claims.containsKey("merchantUniqueId")) {
                merchantTxnId = (String) claims.get("merchantUniqueId");
            }
            if (claims.containsKey("status")) {
                status = (String) claims.get("status");
            }
            if (claims.containsKey("gid")) {
                gid = (String) claims.get("gid");
            }
        }

        if (merchantTxnId == null) {
            return "https://stylenestfashion.com/pay?error=missing_payment";
        }

        QuickPayment payment = quickPaymentRepository.findByPaymentNumber(merchantTxnId).orElse(null);
        if (payment == null) {
            log.error("Quick payment not found in callback: {}", merchantTxnId);
            return "https://stylenestfashion.com/pay?error=payment_not_found";
        }

        boolean isSuccess = "SENT_FOR_CAPTURE".equalsIgnoreCase(status)
                || "COMPLETED".equalsIgnoreCase(status)
                || "SUCCESS".equalsIgnoreCase(status);

        if (isSuccess) {
            payment.setPaymentStatus(PaymentStatus.PAID);
            if (gid != null) {
                payment.setPayglocalGid(gid);
            }
            quickPaymentRepository.save(payment);
            log.info("Quick payment {} confirmed as PAID via PayGlocal (GID: {})", payment.getPaymentNumber(), gid);

            // Send receipt email to customer
            sendReceiptEmail(payment);

            return "https://stylenestfashion.com/pay/receipt?paymentNumber="
                    + payment.getPaymentNumber() + "&status=success";
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            quickPaymentRepository.save(payment);
            log.warn("Quick payment {} failed/cancelled via PayGlocal (Status: {})", payment.getPaymentNumber(), status);

            return "https://stylenestfashion.com/pay/receipt?paymentNumber="
                    + payment.getPaymentNumber() + "&status=failed";
        }
    }

    @Override
    @Transactional(readOnly = true)
    public QuickPaymentResponse getPaymentStatus(String paymentNumber) {
        QuickPayment payment = quickPaymentRepository.findByPaymentNumber(paymentNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Quick payment not found: " + paymentNumber));
        return mapToResponse(payment, null);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<QuickPaymentResponse> getAllQuickPayments() {
        return quickPaymentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(p -> mapToResponse(p, null))
                .toList();
    }

    private void sendReceiptEmail(QuickPayment payment) {
        try {
            emailService.sendQuickPaymentReceiptEmail(
                    payment.getCustomerEmail(),
                    payment.getCustomerName(),
                    payment.getPaymentNumber(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getNote()
            );
            log.info("Sent Quick Payment receipt email for {}", payment.getPaymentNumber());
        } catch (Exception e) {
            log.error("Failed to send Quick Payment receipt email for {}: {}",
                    payment.getPaymentNumber(), e.getMessage());
        }
    }

    private QuickPaymentResponse mapToResponse(QuickPayment payment, String redirectUrl) {
        return QuickPaymentResponse.builder()
                .paymentNumber(payment.getPaymentNumber())
                .customerName(payment.getCustomerName())
                .customerEmail(payment.getCustomerEmail())
                .customerPhone(payment.getCustomerPhone())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .note(payment.getNote())
                .paymentStatus(payment.getPaymentStatus())
                .redirectUrl(redirectUrl)
                .payglocalTxnId(payment.getPayglocalTxnId())
                .payglocalGid(payment.getPayglocalGid())
                .addressLine1(payment.getAddressLine1())
                .city(payment.getCity())
                .state(payment.getState())
                .country(payment.getCountry())
                .postalCode(payment.getPostalCode())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
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
