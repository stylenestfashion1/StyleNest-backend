package com.stylenest.stylenest_backend.dto.bulk;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkOrderResponse {

    private Long id;
    private String bulkOrderNumber;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String shippingAddressLine1;
    private String shippingAddressLine2;
    private String shippingCity;
    private String shippingState;
    private String shippingPostalCode;
    private String shippingCountry;
    private List<BulkOrderItemResponse> items;
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private OrderStatus orderStatus;
    private LocalDateTime createdAt;
    private Boolean invoiceAvailable;
}
