package com.stylenest.stylenest_backend.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.order.GuestOrderRequest;
import com.stylenest.stylenest_backend.dto.order.GuestOrderTrackingRequest;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.OrderMapper;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.service.GuestOrderService;
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

    @Override
    public OrderResponse placeOrder(GuestOrderRequest request) {

        return orderService.placeGuestOrder(request);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse trackOrder(GuestOrderTrackingRequest request) {

        Order order = findVerifiedGuestOrder(request.getOrderNumber(), request.getPhone());

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getInvoicePdf(String orderNumber, String phone) {

        Order order = findVerifiedGuestOrder(orderNumber, phone);

        return invoiceService.generateInvoicePdf(order);
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
