package com.stylenest.stylenest_backend.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LatestOrderResponse {

    private Long orderId;

    private String orderNumber;

    private String customerName;

    private BigDecimal totalAmount;

    private String orderStatus;

    private LocalDateTime createdAt;

}