package com.stylenest.stylenest_backend.service.admin.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.stylenest.stylenest_backend.dto.admin.AdminOrderSearchRequest;
import com.stylenest.stylenest_backend.dto.admin.AdminOrderSummaryResponse;
import com.stylenest.stylenest_backend.dto.admin.OrderStatusUpdateRequest;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.mapper.OrderMapper;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ShipmentRepository;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.InvoiceService;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private EmailService emailService;

    private AdminOrderServiceImpl adminOrderService;

    @BeforeEach
    void setUp() {

        adminOrderService = new AdminOrderServiceImpl(
                orderRepository, shipmentRepository, orderMapper, invoiceService, emailService);
    }

    @Test
    void updateOrderStatus_noLongerSetsPaymentStatus_orderAndPaymentStatusStayIndependent() {

        Order order = Order.builder()
                .id(1L)
                .orderStatus(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PENDING) // COD, still unpaid
                .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(com.stylenest.stylenest_backend.dto.order.OrderResponse.builder().build());

        adminOrderService.updateOrderStatus(1L, OrderStatusUpdateRequest.builder().orderStatus(OrderStatus.COMPLETED).build());

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
        // Marking an order COMPLETED must never silently mark a still-COD-
        // pending payment as settled -- that conflation is exactly what
        // this task removed.
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void updateOrderStatus_completedOrder_cannotBeChangedAgain() {

        Order order = Order.builder().id(1L).orderStatus(OrderStatus.COMPLETED).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderService.updateOrderStatus(
                1L, OrderStatusUpdateRequest.builder().orderStatus(OrderStatus.CANCELLED).build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateOrderStatus_cancelledOrder_cannotBeChangedAgain() {

        Order order = Order.builder().id(1L).orderStatus(OrderStatus.CANCELLED).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderService.updateOrderStatus(
                1L, OrderStatusUpdateRequest.builder().orderStatus(OrderStatus.CONFIRMED).build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void searchOrders_usesRepositorySpecificationAndPageable_noInMemoryFiltering() {

        User registeredUser = User.builder().id(1L).email("rahul@example.com").fullName("Rahul").build();

        Order registeredOrder = Order.builder()
                .id(1L).orderNumber("F21-1001").totalAmount(BigDecimal.TEN)
                .orderStatus(OrderStatus.PENDING).paymentMethod(PaymentMethod.UPI).paymentStatus(PaymentStatus.PAID)
                .user(registeredUser)
                .build();

        Order guestOrder = Order.builder()
                .id(2L).orderNumber("F21-1002").totalAmount(BigDecimal.TEN)
                .orderStatus(OrderStatus.PENDING).paymentMethod(PaymentMethod.COD).paymentStatus(PaymentStatus.PENDING)
                .guestEmail("john@example.com").shippingFullName("John Smith")
                .build();

        Page<Order> page = new PageImpl<>(List.of(registeredOrder, guestOrder));

        when(orderRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Order>>any(), any(Pageable.class)))
                .thenReturn(page);
        when(shipmentRepository.findByOrder(any(Order.class))).thenReturn(Optional.empty());

        Page<AdminOrderSummaryResponse> result = adminOrderService.searchOrders(
                AdminOrderSearchRequest.builder().keyword("F21-100").build());

        assertThat(result.getContent()).hasSize(2);

        AdminOrderSummaryResponse registeredSummary = result.getContent().get(0);
        assertThat(registeredSummary.getIsGuest()).isFalse();
        assertThat(registeredSummary.getCustomerName()).isEqualTo("Rahul");
        assertThat(registeredSummary.getCustomerEmail()).isEqualTo("rahul@example.com");

        AdminOrderSummaryResponse guestSummary = result.getContent().get(1);
        assertThat(guestSummary.getIsGuest()).isTrue();
        assertThat(guestSummary.getCustomerName()).isEqualTo("John Smith");
        assertThat(guestSummary.getCustomerEmail()).isEqualTo("john@example.com");
    }
}
