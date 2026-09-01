package com.stylenest.stylenest_backend.controller;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.payment.EasebuzzInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.EasebuzzInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.GuestPaymentInitiateRequest;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments/easebuzz")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<EasebuzzInitiateResponse>> initiate(
            @Valid @RequestBody EasebuzzInitiateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Payment initiated",
                        paymentService.initiate(request)));
    }

    @PostMapping("/guest/initiate")
    public ResponseEntity<ApiResponse<EasebuzzInitiateResponse>> initiateForGuest(
            @Valid @RequestBody GuestPaymentInitiateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Payment initiated",
                        paymentService.initiateForGuest(request)));
    }

    /**
     * Easebuzz posts here (as the customer's browser, via an auto-submitted
     * form) after the customer finishes on their hosted/seamless payment
     * page -- this is both the success URL (surl) and failure URL (furl);
     * the posted "status" field distinguishes the two. Must stay public:
     * there is no StyleNest JWT on this request, only Easebuzz's own
     * hash-signed payload, which is what actually gets verified.
     */
    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam Map<String, String> responseFields) {

        String redirectUrl = paymentService.handleCallback(responseFields);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }
}
