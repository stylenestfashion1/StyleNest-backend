package com.stylenest.stylenest_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.checkout.CheckoutResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.CheckoutService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @GetMapping
    public ResponseEntity<ApiResponse<CheckoutResponse>> getCheckout() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Checkout fetched successfully",
                        checkoutService.getCheckout()
                ));
    }
}