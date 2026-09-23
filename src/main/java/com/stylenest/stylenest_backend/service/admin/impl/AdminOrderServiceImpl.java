package com.stylenest.stylenest_backend.service.admin.impl;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.admin.AdminOrderSearchRequest;
import com.stylenest.stylenest_backend.dto.admin.AdminOrderSummaryResponse;
import com.stylenest.stylenest_backend.dto.admin.OrderStatusUpdateRequest;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.dto.order.OrderSummaryResponse;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.OrderMapper;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ShipmentRepository;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.InvoiceGenerationService;
import com.stylenest.stylenest_backend.service.InvoiceService;
import com.stylenest.stylenest_backend.service.admin.AdminOrderService;
import com.stylenest.stylenest_backend.specification.OrderSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final OrderMapper orderMapper;
    private final InvoiceService invoiceService;
    private final InvoiceGenerationService invoiceGenerationService;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getAllOrders() {

        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(orderMapper::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrderSummaryResponse> searchOrders(AdminOrderSearchRequest request) {

        Sort sort = Sort.by(
                Sort.Direction.fromString(request.getDirection()),
                request.getSortBy());

        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSizePerPage(),
                sort);

        Page<Order> orders = orderRepository.findAll(
                OrderSpecification.search(request.getKeyword()),
                pageable);

        return orders.map(this::toAdminSummary);
    }

    private AdminOrderSummaryResponse toAdminSummary(Order order) {

        boolean isGuest = order.getUser() == null;

        ShipmentStatus shipmentStatus = shipmentRepository.findByOrder(order)
                .map(Shipment::getShipmentStatus)
                .orElse(null);

        return AdminOrderSummaryResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .totalAmount(order.getTotalAmount())
                .orderStatus(order.getOrderStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .shipmentStatus(shipmentStatus)
                .isGuest(isGuest)
                .customerName(isGuest ? order.getShippingFullName() : order.getUser().getFullName())
                .customerEmail(isGuest ? order.getGuestEmail() : order.getUser().getEmail())
                .createdAt(order.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {

        Order order = findOrder(id);

        return orderMapper.toResponse(order);
    }

    @Override
    public OrderResponse updateOrderStatus(
            Long id,
            OrderStatusUpdateRequest request) {

        Order order = findOrder(id);

        if (order.getOrderStatus() == OrderStatus.CANCELLED
                || order.getOrderStatus() == OrderStatus.COMPLETED
                || order.getOrderStatus() == OrderStatus.DELIVERED) {

            throw new BadRequestException(
                    "Order status cannot be changed.");
        }

        // Order status and payment status are independent concerns -- this
        // no longer sets paymentStatus as a side effect of an order-status
        // change. Payment status is only ever set by actual payment events
        // (COD placement, verified Easebuzz callback) in OrderServiceImpl /
        // PaymentServiceImpl. Shipment/delivery progression now lives in
        // ShipmentService, not here.
        order.setOrderStatus(request.getOrderStatus());

        Order updatedOrder = orderRepository.save(order);

        sendStatusUpdateEmailIfPossible(updatedOrder);

        return orderMapper.toResponse(updatedOrder);
    }

    // Best-effort, same as order confirmation: a Brevo outage must never
    // fail an admin's status update, which has already been durably saved.
    private void sendStatusUpdateEmailIfPossible(Order order) {

        boolean isGuest = order.getUser() == null;

        String recipient = isGuest ? order.getGuestEmail() : order.getUser().getEmail();

        if (recipient == null || recipient.isBlank()) {
            return;
        }

        String customerName = isGuest ? order.getShippingFullName() : order.getUser().getFullName();

        try {
            emailService.sendOrderStatusUpdateEmail(
                    recipient, customerName, order.getOrderNumber(), order.getOrderStatus());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public InvoiceResponse getInvoiceView(Long id) {

        return invoiceService.buildView(invoiceGenerationService.generateForRetailOrder(findOrder(id)));
    }

    @Override
    public byte[] getInvoicePdf(Long id) {

        return invoiceService.generatePdf(invoiceGenerationService.generateForRetailOrder(findOrder(id)));
    }

    @Override
    public void resendInvoiceEmail(Long id) {

        Order order = findOrder(id);
        var invoice = invoiceGenerationService.generateForRetailOrder(order);
        byte[] pdf = invoiceService.generatePdf(invoice);

        String recipient = order.getUser() != null ? order.getUser().getEmail() : order.getGuestEmail();

        if (recipient == null || recipient.isBlank()) {
            throw new BadRequestException("This order has no email address on file.");
        }

        emailService.sendInvoiceEmail(
                recipient, order.getShippingFullName(), invoice.getInvoiceNumber(),
                order.getOrderNumber(), order.getTotalAmount(), pdf);
    }

    private Order findOrder(Long id) {

        return orderRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Order not found with id: " + id));
    }
}
