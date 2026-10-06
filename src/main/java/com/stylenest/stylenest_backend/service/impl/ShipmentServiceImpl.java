package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.entity.OrderItem;
import com.stylenest.stylenest_backend.service.shipping.DtdcRateCalculatorService;

import com.stylenest.stylenest_backend.dto.shipment.DtdcBookingRequest;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentResponse;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentUpdateRequest;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.entity.ShipmentHistory;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.DtdcApiException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.ShipmentMapper;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ShipmentHistoryRepository;
import com.stylenest.stylenest_backend.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.ShipmentService;
import com.stylenest.stylenest_backend.service.courier.CourierTrackingService;

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

    private static final Logger log = LoggerFactory.getLogger(ShipmentServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentHistoryRepository shipmentHistoryRepository;
    private final ShipmentMapper shipmentMapper;
    private final CourierTrackingService courierTrackingService;
    private final EmailService emailService;

    @Override
    public ShipmentResponse updateShipment(Long orderId, ShipmentUpdateRequest request) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Shipment shipment = shipmentRepository.findByOrder(order)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This order does not have a shipment yet (payment may still be pending)."));

        ShipmentStatus previousStatus = shipment.getShipmentStatus();
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

        if (request.getShipmentStatus() != previousStatus || request.getTrackingNumber() != null) {
            sendShipmentEmailIfPossible(order, shipment);
        }

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

    @Override
    public ShipmentResponse bookDtdcShipment(Long orderId, DtdcBookingRequest request) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Shipment shipment = shipmentRepository.findByOrder(order)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This order does not have a shipment yet (payment may still be pending)."));

        // Idempotency guard: a shipment that already has a tracking number
        // (whether from DTDC or a manual admin entry) must never be booked
        // again -- this is what stops a double-click or retry from
        // creating a second DTDC consignment/AWB for the same shipment.
        if (shipment.getTrackingNumber() != null) {
            throw new BadRequestException(
                    "This shipment already has a tracking number (" + shipment.getTrackingNumber()
                            + "); it cannot be booked with DTDC again.");
        }

        if (TERMINAL.contains(shipment.getShipmentStatus())) {
            throw new BadRequestException("Cannot book a shipment that is already " + shipment.getShipmentStatus() + ".");
        }

        // DTDC's Order Upload API schema has no destination-country field
        // at all (origin_details/destination_details are pincode/city/
        // state only) -- it is documented as a domestic-only API, so
        // booking is restricted to India destinations rather than silently
        // sending an international address it was never designed for.
        if (order.getShippingCountryCode() == null || !order.getShippingCountryCode().equalsIgnoreCase("IN")) {
            throw new BadRequestException(
                    "DTDC booking only supports domestic (India) orders; this order ships to "
                            + order.getShippingCountryCode() + ".");
        }

        BigDecimal weightKg = request != null && request.getWeightKg() != null
                ? request.getWeightKg() : computeAutoWeightKg(order);
        BigDecimal lengthCm = request != null && request.getLengthCm() != null
                ? request.getLengthCm() : BigDecimal.valueOf(30.0);
        BigDecimal widthCm = request != null && request.getWidthCm() != null
                ? request.getWidthCm() : BigDecimal.valueOf(25.0);
        int numPieces = request != null && request.getNumPieces() != null
                ? request.getNumPieces() : computeTotalPieces(order);
        BigDecimal heightCm = request != null && request.getHeightCm() != null
                ? request.getHeightCm() : BigDecimal.valueOf(Math.min(50.0, Math.max(3.0, numPieces * 2.5)));

        CourierTrackingService.ShipmentBookingRequest bookingRequest = new CourierTrackingService.ShipmentBookingRequest(
                order.getOrderNumber(),
                order.getShippingFullName(),
                order.getShippingPhone(),
                order.getShippingAddressLine1(),
                order.getShippingAddressLine2(),
                order.getShippingCity(),
                order.getShippingState(),
                order.getShippingPostalCode(),
                order.getPaymentMethod() == PaymentMethod.COD,
                order.getPaymentMethod() == PaymentMethod.COD ? order.getTotalAmount() : null,
                order.getTotalAmount(),
                weightKg,
                lengthCm,
                widthCm,
                heightCm,
                numPieces);

        CourierTrackingService.BookingResult result = courierTrackingService.bookShipment(bookingRequest);

        shipment.setTrackingNumber(result.providerReferenceNumber());
        shipment.setCourierName("DTDC");

        if (shipment.getShipmentStatus() == ShipmentStatus.PROCESSING) {
            shipment.setShipmentStatus(ShipmentStatus.PACKED);
        }

        shipment = shipmentRepository.save(shipment);

        shipmentHistoryRepository.save(ShipmentHistory.builder()
                .shipment(shipment)
                .status(shipment.getShipmentStatus())
                .description("Booked with DTDC (AWB: " + result.providerReferenceNumber() + ").")
                .build());

        List<ShipmentHistory> history = shipmentHistoryRepository.findByShipmentOrderByTimestampAsc(shipment);

        sendShipmentEmailIfPossible(order, shipment);

        return shipmentMapper.toResponse(shipment, history);
    }

    @Override
    public ShipmentResponse cancelDtdcShipment(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Shipment shipment = shipmentRepository.findByOrder(order)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This order does not have a shipment yet (payment may still be pending)."));

        requireDtdcBooked(shipment);

        if (TERMINAL.contains(shipment.getShipmentStatus())) {
            throw new BadRequestException("Shipment status is final and cannot be changed.");
        }

        courierTrackingService.cancelShipment(shipment.getTrackingNumber());

        shipment.setShipmentStatus(ShipmentStatus.CANCELLED);
        shipment = shipmentRepository.save(shipment);

        shipmentHistoryRepository.save(ShipmentHistory.builder()
                .shipment(shipment)
                .status(ShipmentStatus.CANCELLED)
                .description("Cancelled with DTDC.")
                .build());

        List<ShipmentHistory> history = shipmentHistoryRepository.findByShipmentOrderByTimestampAsc(shipment);

        sendShipmentEmailIfPossible(order, shipment);

        return shipmentMapper.toResponse(shipment, history);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] fetchDtdcLabel(Long orderId) {

        Shipment shipment = shipmentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("This order does not have a shipment yet."));

        requireDtdcBooked(shipment);

        return courierTrackingService.fetchLabel(shipment.getTrackingNumber());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShipmentResponse refreshDtdcTracking(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        Shipment shipment = shipmentRepository.findByOrder(order)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This order does not have a shipment yet (payment may still be pending)."));

        requireDtdcBooked(shipment);

        CourierTrackingService.CourierTrackingSnapshot snapshot = courierTrackingService
                .fetchTracking("DTDC", shipment.getTrackingNumber())
                .orElseThrow(() -> new DtdcApiException("DTDC returned no tracking details for this shipment."));

        List<ShipmentHistory> history = shipmentHistoryRepository.findByShipmentOrderByTimestampAsc(shipment);

        // A terminal shipment (delivered/cancelled/returned) is never
        // downgraded by a tracking pull -- only a still-in-flight shipment
        // is updated. DTDC's own reported status is trusted as-is here
        // (not run through updateShipment's stricter "no backward
        // transition" rule), since a real courier can legitimately report
        // what looks like a step back (e.g. OUT_FOR_DELIVERY -> IN_TRANSIT
        // after a failed delivery attempt).
        boolean statusChanged = !TERMINAL.contains(shipment.getShipmentStatus())
                && snapshot.status() != shipment.getShipmentStatus();

        if (statusChanged) {

            shipment.setShipmentStatus(snapshot.status());

            if (snapshot.status() == ShipmentStatus.SHIPPED && shipment.getShippedAt() == null) {
                shipment.setShippedAt(LocalDateTime.now());
            }

            if (snapshot.status() == ShipmentStatus.DELIVERED && shipment.getDeliveredAt() == null) {
                shipment.setDeliveredAt(LocalDateTime.now());
                if (order.getOrderStatus() != com.stylenest.stylenest_backend.enums.OrderStatus.DELIVERED
                        && order.getOrderStatus() != com.stylenest.stylenest_backend.enums.OrderStatus.COMPLETED) {
                    order.setOrderStatus(com.stylenest.stylenest_backend.enums.OrderStatus.DELIVERED);
                    orderRepository.save(order);
                }
            }

            shipment = shipmentRepository.save(shipment);

            ShipmentHistory newEntry = shipmentHistoryRepository.save(ShipmentHistory.builder()
                    .shipment(shipment)
                    .status(snapshot.status())
                    .description(snapshot.description() == null
                            ? "Updated from DTDC tracking."
                            : snapshot.description() + " (via DTDC)")
                    .location(snapshot.location())
                    .build());

            history = new ArrayList<>(history);
            history.add(newEntry);

            sendShipmentEmailIfPossible(order, shipment);
        }

        return shipmentMapper.toResponse(shipment, history);
    }

    private void sendShipmentEmailIfPossible(Order order, Shipment shipment) {

        boolean isGuest = order.getUser() == null;
        String recipient = isGuest ? order.getGuestEmail() : order.getUser().getEmail();

        if (recipient == null || recipient.isBlank()) {
            return;
        }

        String customerName = isGuest ? order.getShippingFullName() : order.getUser().getFullName();

        try {
            emailService.sendShipmentUpdateEmail(
                    recipient,
                    customerName,
                    order.getOrderNumber(),
                    shipment.getCourierName(),
                    shipment.getTrackingNumber(),
                    shipment.getShipmentStatus());
        } catch (Exception ex) {
            log.error("Failed to send shipment update email for order {}", order.getOrderNumber(), ex);
        }
    }

    private void requireDtdcBooked(Shipment shipment) {

        if (shipment.getTrackingNumber() == null || !"DTDC".equalsIgnoreCase(shipment.getCourierName())) {
            throw new BadRequestException("This shipment was not booked with DTDC.");
        }
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

    private BigDecimal computeAutoWeightKg(Order order) {
        BigDecimal totalWeightGrams = BigDecimal.ZERO;
        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                var spec = DtdcRateCalculatorService.PhysicalItemSpec.fromVariant(item.getProductVariant(), qty);
                totalWeightGrams = totalWeightGrams.add(spec.computeChargeableWeightGrams());
            }
        }
        if (totalWeightGrams.compareTo(BigDecimal.ZERO) <= 0) {
            totalWeightGrams = DtdcRateCalculatorService.DEFAULT_ITEM_WEIGHT_GRAMS;
        }
        return totalWeightGrams.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
                .max(BigDecimal.valueOf(0.35));
    }

    private int computeTotalPieces(Order order) {
        int total = 0;
        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                total += (item.getQuantity() != null ? item.getQuantity() : 1);
            }
        }
        return Math.max(1, total);
    }
}
