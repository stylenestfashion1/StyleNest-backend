package com.stylenest.stylenest_backend.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminOrderSummaryResponse {

    private Long id;

    private String orderNumber;

    private Currency currency;

    private BigDecimal totalAmount;

    private OrderStatus orderStatus;

    private PaymentMethod paymentMethod;

    private PaymentStatus paymentStatus;

    private ShipmentStatus shipmentStatus;

    private Boolean isGuest;

    private String customerName;

    private String customerEmail;

    private LocalDateTime createdAt;
}
