package com.stylenest.stylenest_backend.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.dto.quickpay.QuickPaymentInitiateRequest;
import com.stylenest.stylenest_backend.dto.quickpay.QuickPaymentResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.QuickPaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/quick-pay")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class QuickPaymentController {

    private final QuickPaymentService quickPaymentService;
    private final ObjectMapper objectMapper;

    /**
     * Customer submits Quick Payment form. Returns PayGlocal redirect URL.
     */
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<QuickPaymentResponse>> initiateQuickPayment(
            @Valid @RequestBody QuickPaymentInitiateRequest request) {

        log.info("Quick payment initiate request received for {} ({} {})",
                request.getCustomerEmail(), request.getCurrency(), request.getAmount());

        QuickPaymentResponse response = quickPaymentService.initiateQuickPayment(request);
        return ResponseEntity.ok(ApiResponse.success("Payment initiated successfully", response));
    }

    /**
     * PayGlocal GET callback redirect upon payment completion or cancellation.
     */
    @GetMapping("/callback")
    public RedirectView handleCallbackGet(@RequestParam Map<String, String> queryParams) {
        log.info("Received PayGlocal Quick Payment GET callback: {}", queryParams);
        String targetUrl = quickPaymentService.handleCallback(queryParams);
        return new RedirectView(targetUrl);
    }

    /**
     * PayGlocal POST callback redirect upon payment completion or cancellation.
     */
    @PostMapping("/callback")
    public RedirectView handleCallbackPost(
            @RequestParam(required = false) Map<String, String> formData,
            @RequestBody(required = false) String rawBody) {

        log.info("Received PayGlocal Quick Payment POST callback. Form: {}, Body: {}", formData, rawBody);
        Map<String, String> combined = new HashMap<>();
        if (formData != null) {
            combined.putAll(formData);
        }
        if (rawBody != null && rawBody.trim().startsWith("{")) {
            try {
                Map<String, String> bodyMap = objectMapper.readValue(rawBody, new TypeReference<Map<String, String>>() {});
                combined.putAll(bodyMap);
            } catch (Exception ignored) {}
        }

        String targetUrl = quickPaymentService.handleCallback(combined);
        return new RedirectView(targetUrl);
    }

    /**
     * Fetch status and details for receipt page.
     */
    @GetMapping("/{paymentNumber}")
    public ResponseEntity<ApiResponse<QuickPaymentResponse>> getPaymentStatus(
            @PathVariable String paymentNumber) {

        QuickPaymentResponse response = quickPaymentService.getPaymentStatus(paymentNumber);
        return ResponseEntity.ok(ApiResponse.success("Payment status retrieved", response));
    }
}
