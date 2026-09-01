package com.stylenest.stylenest_backend.dto.shipment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.stylenest.stylenest_backend.enums.ShipmentStatus;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentResponse {

    private Long id;

    private Long orderId;

    private ShipmentStatus shipmentStatus;

    private String trackingNumber;

    private String courierName;

    private LocalDate estimatedDeliveryDate;

    private LocalDateTime shippedAt;

    private LocalDateTime deliveredAt;

    private List<ShipmentHistoryResponse> history;
}
