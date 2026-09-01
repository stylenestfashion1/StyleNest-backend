package com.stylenest.stylenest_backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.order.GuestOrderRequest;
import com.stylenest.stylenest_backend.dto.order.GuestOrderTrackingRequest;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.GuestOrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * No login/registration/OTP required -- every endpoint here is public.
 * Order lookup/invoice access is gated by orderNumber + phone instead of a
 * JWT (see GuestOrderServiceImpl.findVerifiedGuestOrder).
 */
@RestController
@RequestMapping("/api/guest/orders")
@RequiredArgsConstructor
public class GuestOrderController {

    private final GuestOrderService guestOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @Valid @RequestBody GuestOrderRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Order placed successfully",
                        guestOrderService.placeOrder(request)));
    }

    @PostMapping("/track")
    public ResponseEntity<ApiResponse<OrderResponse>> trackOrder(
            @Valid @RequestBody GuestOrderTrackingRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Order fetched successfully",
                        guestOrderService.trackOrder(request)));
    }

    @GetMapping("/invoice")
    public ResponseEntity<byte[]> getInvoice(
            @RequestParam String orderNumber,
            @RequestParam String phone) {

        byte[] pdf = guestOrderService.getInvoicePdf(orderNumber, phone);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Invoice-" + orderNumber + ".pdf\"")
                .body(pdf);
    }
}
