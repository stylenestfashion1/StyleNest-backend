package com.stylenest.stylenest_backend.dto.quickpay;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.PaymentStatus;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickPaymentResponse {

    private String paymentNumber;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private BigDecimal amount;
    private String currency;
    private String note;
    private PaymentStatus paymentStatus;
    private String redirectUrl;
    private LocalDateTime createdAt;
}
