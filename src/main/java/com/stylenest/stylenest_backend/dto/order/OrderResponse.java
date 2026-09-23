package com.stylenest.stylenest_backend.dto.order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;

    private String orderNumber;

    private BigDecimal totalAmount;

    private PaymentMethod paymentMethod;

    private PaymentStatus paymentStatus;

    private OrderStatus orderStatus;

    private LocalDateTime createdAt;

    private List<OrderItemResponse> items;

    private Boolean isGuest;

    private String email;

    private ShippingAddressSnapshotResponse shippingAddress;

    private ShipmentStatus shipmentStatus;

    private String trackingNumber;

    private String courierName;

    private LocalDate estimatedDeliveryDate;

    private Boolean invoiceAvailable;
}
