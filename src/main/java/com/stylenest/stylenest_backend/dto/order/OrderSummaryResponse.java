package com.stylenest.stylenest_backend.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.enums.OrderStatus;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSummaryResponse {

    private Long id;

    private String orderNumber;

    private Currency currency;

    private BigDecimal totalAmount;

    private OrderStatus orderStatus;

    private LocalDateTime createdAt;
}