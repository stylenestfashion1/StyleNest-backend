package com.stylenest.stylenest_backend.dto.invoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.*;

/**
 * Full JSON view of a GST invoice -- built entirely from a persisted
 * {@link com.stylenest.stylenest_backend.entity.Invoice} snapshot, never
 * recomputed from live product/order data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponse {

    private Long invoiceId;
    private String invoiceNumber;
    private LocalDateTime invoiceDate;

    private String orderType; // RETAIL | BULK
    private Long orderId;
    private String orderNumber;
    private Boolean isGuest;

    // Seller
    private String sellerName;
    private String sellerAddress;
    private String sellerPhone;
    private String sellerEmail;
    private String sellerState;
    private String sellerGstin;
    private String sellerCin;

    // Customer / bill-to
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String customerGstin;
    private String billingAddressLine1;
    private String billingAddressLine2;
    private String billingCity;
    private String billingState;
    private String billingPostalCode;
    private String billingCountry;

    private List<InvoiceLineItemResponse> items;

    private BigDecimal taxableAmount;
    private BigDecimal totalDiscount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal shippingCharge;
    private BigDecimal roundOff;
    private BigDecimal grandTotal;
    private Boolean interState;

    private List<InvoiceTaxBreakupResponse> taxBreakup;

    private String amountInWords;

    private String paymentMethod;
    private String paymentStatus;
    private BigDecimal amountReceived;
    private BigDecimal balanceDue;
}
