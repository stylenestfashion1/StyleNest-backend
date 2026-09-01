package com.stylenest.stylenest_backend.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.shipment.ShipmentHistoryResponse;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentResponse;
import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.entity.ShipmentHistory;

@Component
public class ShipmentMapper {

    public ShipmentResponse toResponse(Shipment shipment, List<ShipmentHistory> history) {

        List<ShipmentHistoryResponse> historyResponses = history.stream()
                .map(this::toHistoryResponse)
                .toList();

        return ShipmentResponse.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrder().getId())
                .shipmentStatus(shipment.getShipmentStatus())
                .trackingNumber(shipment.getTrackingNumber())
                .courierName(shipment.getCourierName())
                .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
                .shippedAt(shipment.getShippedAt())
                .deliveredAt(shipment.getDeliveredAt())
                .history(historyResponses)
                .build();
    }

    private ShipmentHistoryResponse toHistoryResponse(ShipmentHistory history) {

        return ShipmentHistoryResponse.builder()
                .status(history.getStatus())
                .timestamp(history.getTimestamp())
                .description(history.getDescription())
                .location(history.getLocation())
                .build();
    }
}
