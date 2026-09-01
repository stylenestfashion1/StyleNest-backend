package com.stylenest.stylenest_backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.shipment.ShipmentResponse;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentUpdateRequest;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.entity.ShipmentHistory;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.ShipmentMapper;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ShipmentHistoryRepository;
import com.stylenest.stylenest_backend.repository.ShipmentRepository;
import com.stylenest.stylenest_backend.service.ShipmentService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ShipmentServiceImpl implements ShipmentService {

    // Deliberately simple, not a rigid state machine: skipping ahead (e.g.
    // PACKED -> OUT_FOR_DELIVERY) is allowed since real couriers don't
    // always report every intermediate step; only moving backward, or
    // changing a terminal shipment, is rejected.
    private static final List<ShipmentStatus> PROGRESSION = List.of(
            ShipmentStatus.PROCESSING,
            ShipmentStatus.PACKED,
            ShipmentStatus.SHIPPED,
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.OUT_FOR_DELIVERY,
            ShipmentStatus.DELIVERED);

    private static final Set<ShipmentStatus> TERMINAL = Set.of(
            ShipmentStatus.DELIVERED,
            ShipmentStatus.CANCELLED,
            ShipmentStatus.RETURNED);

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentHistoryRepository shipmentHistoryRepository;
    private final ShipmentMapper shipmentMapper;

    @Override
    public ShipmentResponse updateShipment(Long orderId, ShipmentUpdateRequest request) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Shipment shipment = shipmentRepository.findByOrder(order)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This order does not have a shipment yet (payment may still be pending)."));

        validateTransition(shipment.getShipmentStatus(), request.getShipmentStatus());

        shipment.setShipmentStatus(request.getShipmentStatus());

        if (request.getTrackingNumber() != null) {
            shipment.setTrackingNumber(request.getTrackingNumber());
        }

        if (request.getCourierName() != null) {
            shipment.setCourierName(request.getCourierName());
        }

        if (request.getEstimatedDeliveryDate() != null) {
            shipment.setEstimatedDeliveryDate(request.getEstimatedDeliveryDate());
        }

        if (request.getShipmentStatus() == ShipmentStatus.SHIPPED && shipment.getShippedAt() == null) {
            shipment.setShippedAt(LocalDateTime.now());
        }

        if (request.getShipmentStatus() == ShipmentStatus.DELIVERED && shipment.getDeliveredAt() == null) {
            shipment.setDeliveredAt(LocalDateTime.now());
        }

        shipment = shipmentRepository.save(shipment);

        shipmentHistoryRepository.save(ShipmentHistory.builder()
                .shipment(shipment)
                .status(request.getShipmentStatus())
                .description(request.getDescription())
                .location(request.getLocation())
                .build());

        List<ShipmentHistory> history = shipmentHistoryRepository.findByShipmentOrderByTimestampAsc(shipment);

        return shipmentMapper.toResponse(shipment, history);
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentByOrderId(Long orderId) {

        Shipment shipment = shipmentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("This order does not have a shipment yet."));

        List<ShipmentHistory> history = shipmentHistoryRepository.findByShipmentOrderByTimestampAsc(shipment);

        return shipmentMapper.toResponse(shipment, history);
    }

    private void validateTransition(ShipmentStatus current, ShipmentStatus next) {

        if (current == next) {
            return; // no-op status change -- tracking#/courier/ETA can still be updated
        }

        if (TERMINAL.contains(current)) {
            throw new BadRequestException("Shipment status is final and cannot be changed.");
        }

        if (next == ShipmentStatus.CANCELLED || next == ShipmentStatus.RETURNED) {
            return; // always reachable from a non-terminal state
        }

        int currentIndex = PROGRESSION.indexOf(current);
        int nextIndex = PROGRESSION.indexOf(next);

        if (currentIndex < 0 || nextIndex <= currentIndex) {
            throw new BadRequestException("Shipment status cannot move backward.");
        }
    }
}
