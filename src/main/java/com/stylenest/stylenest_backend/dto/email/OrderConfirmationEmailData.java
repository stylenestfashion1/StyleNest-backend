package com.stylenest.stylenest_backend.dto.email;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.stylenest.stylenest_backend.dto.invoice.InvoiceItemResponse;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;

import lombok.*;

/**
 * Everything the confirmation email template needs, built purely from
 * already-stored Order data -- never recomputed/guessed. Reuses
 * InvoiceItemResponse for line items rather than a duplicate line-item
 * model, since the invoice and the confirmation email show the same data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderConfirmationEmailData {

    private String customerName;

    private String orderNumber;

    private String invoiceNumber;

    private LocalDateTime orderDate;

    private List<InvoiceItemResponse> items;

    private BigDecimal subtotal;

    private BigDecimal totalAmount;

    private PaymentMethod paymentMethod;

    private PaymentStatus paymentStatus;

    private OrderStatus orderStatus;

    private String shippingAddressLine1;

    private String shippingAddressLine2;

    private String shippingCity;

    private String shippingState;

    private String shippingPostalCode;

    private String shippingCountry;
}
