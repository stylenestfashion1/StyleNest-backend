package com.stylenest.stylenest_backend.dto.payment;

import java.math.BigDecimal;

import com.stylenest.stylenest_backend.enums.Currency;

import lombok.*;

/**
 * Only ever contains information that is safe to hand to the frontend --
 * the public Razorpay Key ID (never the Key Secret or webhook secret) plus
 * exactly what the frontend needs to open Razorpay Checkout. amountMinor
 * is the exact server-computed value (paise/cents) the frontend passes
 * straight through to Razorpay Checkout, so the frontend never computes
 * or can alter the charged amount itself.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayInitiateResponse {

    private Long orderId;

    private String orderNumber;

    private String razorpayOrderId;

    private String razorpayKeyId;

    // Human-readable amount (e.g. 1499.00), for display only.
    private BigDecimal amount;

    // The exact integer minor-unit amount (paise/cents) Razorpay Checkout expects.
    private long amountMinor;

    private Currency currency;
}
