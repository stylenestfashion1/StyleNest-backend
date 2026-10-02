package com.stylenest.stylenest_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.shipping.ShippingCalculationRequest;
import com.stylenest.stylenest_backend.dto.shipping.ShippingCalculationResponse;
import com.stylenest.stylenest_backend.service.shipping.ShippingCalculationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingCalculationService shippingCalculationService;

    @PostMapping("/calculate")
    public ResponseEntity<ShippingCalculationResponse> calculateShipping(
            @Valid @RequestBody ShippingCalculationRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserEmail = (authentication != null && authentication.isAuthenticated())
                ? authentication.getName() : null;

        ShippingCalculationResponse response = shippingCalculationService.calculateForRequest(
                request, currentUserEmail);

        return ResponseEntity.ok(response);
    }
}
