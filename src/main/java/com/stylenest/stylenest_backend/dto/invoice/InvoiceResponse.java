package com.stylenest.stylenest_backend.dto.invoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;

import lombok.*;

/**
 * JSON view of an invoice -- built entirely from an Order's retained data
 * (never recomputed from live product prices). orderNumber doubles as the
 * invoice number; order creation time doubles as the invoice date.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponse {

    private String invoiceNumber;

    private LocalDateTime invoiceDate;

    private Long orderId;

    private String orderNumber;

    private Boolean isGuest;

    private String customerName;

    private String customerEmail;

    private String customerPhone;

    private String shippingAddressLine1;

    private String shippingAddressLine2;

    private String shippingCity;

    private String shippingState;

    private String shippingPostalCode;

    private String shippingCountry;

    private List<InvoiceItemResponse> items;

    private BigDecimal totalAmount;

    private PaymentMethod paymentMethod;

    private PaymentStatus paymentStatus;
}
