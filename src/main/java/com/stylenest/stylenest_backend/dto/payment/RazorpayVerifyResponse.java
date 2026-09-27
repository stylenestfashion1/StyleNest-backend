package com.stylenest.stylenest_backend.dto.payment;

import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentStatus;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayVerifyResponse {

    private Long orderId;

    private String orderNumber;

    // Null for a guest order (no account to navigate an authenticated /orders/{id} page for).
    private Boolean isGuest;

    private PaymentStatus paymentStatus;

    private OrderStatus orderStatus;
}
