package com.stylenest.stylenest_backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import com.stylenest.stylenest_backend.dto.bulk.payment.PayGlocalInitiateRequest;
import com.stylenest.stylenest_backend.dto.bulk.payment.PayGlocalInitiateResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.payglocal.PayGlocalService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
public class BulkPaymentController {

    private final PayGlocalService payGlocalService;

    /**
     * Customer initiates PayGlocal checkout for their wholesale order.
     * Protected by X-Bulk-Token header via BulkAccessInterceptor.
     */
    @PostMapping("/api/bulk/customer/payments/initiate")
    public ResponseEntity<ApiResponse<PayGlocalInitiateResponse>> initiatePayment(
            @Valid @RequestBody PayGlocalInitiateRequest request) {

        PayGlocalInitiateResponse response = payGlocalService.initiatePayment(request.getBulkOrderNumber());
        return ResponseEntity.ok(ApiResponse.success("PayGlocal payment initiated", response));
    }

    /**
     * Browser callback URL called by PayGlocal upon payment completion.
     * Public endpoint -- redirects buyer to the StyleNest frontend order confirmation page.
     */
    @PostMapping("/api/bulk/payments/payglocal/callback")
    public RedirectView handleCallback(@RequestParam Map<String, String> formData) {
        log.info("Received PayGlocal browser callback redirect");
        String targetUrl = payGlocalService.handleCallback(formData);
        return new RedirectView(targetUrl);
    }

    /**
     * Server-to-server webhook sent by PayGlocal.
     * Public endpoint -- processes background payment status updates.
     */
    @PostMapping("/api/bulk/payments/payglocal/webhook")
    public ResponseEntity<ApiResponse<String>> handleWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "x-gl-token", required = false) String jwsHeader) {

        log.info("Received PayGlocal server webhook");
        payGlocalService.handleWebhook(payload, jwsHeader);
        return ResponseEntity.ok(ApiResponse.success("Webhook processed", "OK"));
    }
}
