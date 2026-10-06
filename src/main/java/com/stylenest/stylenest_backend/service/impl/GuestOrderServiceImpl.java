package com.stylenest.stylenest_backend.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.dto.order.GuestOrderRequest;
import com.stylenest.stylenest_backend.dto.order.GuestOrderTrackingRequest;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.OrderMapper;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.service.GuestOrderService;
import com.stylenest.stylenest_backend.service.InvoiceGenerationService;
import com.stylenest.stylenest_backend.service.InvoiceService;
import com.stylenest.stylenest_backend.service.OrderService;

import lombok.RequiredArgsConstructor;

/**
 * Deliberately separate from OrderServiceImpl: guest endpoints have no
 * authenticated principal (no SecurityContextHolder user) at all, so the
 * access-control model here is entirely different -- a shared
 * order-number-plus-phone verification gate instead of an owning-user
 * check.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GuestOrderServiceImpl implements GuestOrderService {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final InvoiceService invoiceService;
    private final InvoiceGenerationService invoiceGenerationService;
    private final com.stylenest.stylenest_backend.service.ShipmentService shipmentService;
    private final com.stylenest.stylenest_backend.repository.ShipmentRepository shipmentRepository;

    @Override
    public OrderResponse placeOrder(GuestOrderRequest request) {

        return orderService.placeGuestOrder(request);
    }

    @Override
    public OrderResponse trackOrder(GuestOrderTrackingRequest request) {

        Order order = findVerifiedGuestOrder(request.getOrderNumber(), request.getPhone());

        refreshDtdcTrackingSilently(order);

        Order freshOrder = orderRepository.findById(order.getId()).orElse(order);

        return orderMapper.toResponse(freshOrder);
    }

    private void refreshDtdcTrackingSilently(Order order) {
        try {
            var shipmentOpt = shipmentRepository.findByOrder(order);
            if (shipmentOpt.isPresent()) {
                var shipment = shipmentOpt.get();
                if ("DTDC".equalsIgnoreCase(shipment.getCourierName())
                        && shipment.getTrackingNumber() != null
                        && !shipment.getTrackingNumber().isBlank()
                        && shipment.getShipmentStatus() != com.stylenest.stylenest_backend.enums.ShipmentStatus.DELIVERED
                        && shipment.getShipmentStatus() != com.stylenest.stylenest_backend.enums.ShipmentStatus.CANCELLED
                        && shipment.getShipmentStatus() != com.stylenest.stylenest_backend.enums.ShipmentStatus.RETURNED) {
                    shipmentService.refreshDtdcTracking(order.getId());
                }
            }
        } catch (Exception ignored) {
            // Best-effort live refresh: cached tracking info still served if DTDC is slow
        }
    }

    @Override
    public InvoiceResponse getInvoiceView(String orderNumber, String phone) {

        Order order = findVerifiedGuestOrder(orderNumber, phone);

        return invoiceService.buildView(invoiceGenerationService.generateForRetailOrder(order));
    }

    @Override
    public byte[] getInvoicePdf(String orderNumber, String phone) {

        Order order = findVerifiedGuestOrder(orderNumber, phone);

        return invoiceService.generatePdf(invoiceGenerationService.generateForRetailOrder(order));
    }

    /**
     * The one place guest access control lives. Strictly scoped to
     * order.user == null -- a guest must never be able to phone-guess into
     * a registered customer's order. Never distinguishes "wrong order
     * number" from "wrong phone" in the thrown error, to avoid leaking
     * whether an order number exists at all.
     */
    private Order findVerifiedGuestOrder(String orderNumber, String phone) {

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .filter(o -> o.getUser() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));

        if (!normalizePhone(order.getShippingPhone()).equals(normalizePhone(phone))) {
            throw new ResourceNotFoundException("Order not found.");
        }

        return order;
    }

    private String normalizePhone(String phone) {

        // Strips whitespace/dashes/parens only -- does NOT strip '+' or
        // digits, so a missing country code never accidentally matches a
        // present one.
        return phone == null ? "" : phone.replaceAll("[\\s\\-()]", "");
    }
}
