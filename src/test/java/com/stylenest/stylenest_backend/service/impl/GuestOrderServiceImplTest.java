package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stylenest.stylenest_backend.dto.order.GuestOrderTrackingRequest;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.OrderMapper;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.service.InvoiceService;
import com.stylenest.stylenest_backend.service.OrderService;

@ExtendWith(MockitoExtension.class)
class GuestOrderServiceImplTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private InvoiceService invoiceService;

    private GuestOrderServiceImpl guestOrderService;

    private Order guestOrder;

    @BeforeEach
    void setUp() {

        guestOrderService = new GuestOrderServiceImpl(orderService, orderRepository, orderMapper, invoiceService);

        guestOrder = Order.builder()
                .id(1L)
                .orderNumber("F21-GUEST-1")
                .user(null)
                .guestEmail("guest@example.com")
                .shippingPhone("9998887777")
                .build();
    }

    @Test
    void trackOrder_correctOrderNumberAndPhone_succeeds() {

        when(orderRepository.findByOrderNumber("F21-GUEST-1")).thenReturn(Optional.of(guestOrder));
        when(orderMapper.toResponse(guestOrder)).thenReturn(OrderResponse.builder().id(1L).build());

        OrderResponse response = guestOrderService.trackOrder(
                GuestOrderTrackingRequest.builder().orderNumber("F21-GUEST-1").phone("9998887777").build());

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void trackOrder_wrongPhone_throwsNotFound_notAnAuthError() {

        when(orderRepository.findByOrderNumber("F21-GUEST-1")).thenReturn(Optional.of(guestOrder));

        assertThatThrownBy(() -> guestOrderService.trackOrder(
                GuestOrderTrackingRequest.builder().orderNumber("F21-GUEST-1").phone("0000000000").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void trackOrder_wrongOrderNumber_throwsNotFound() {

        when(orderRepository.findByOrderNumber("F21-DOES-NOT-EXIST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guestOrderService.trackOrder(
                GuestOrderTrackingRequest.builder().orderNumber("F21-DOES-NOT-EXIST").phone("9998887777").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void trackOrder_registeredCustomersOrder_cannotBeAccessedEvenWithCorrectPhone() {

        // The critical security case: a guest must never be able to
        // phone-guess into a REGISTERED customer's order, even if they
        // happen to know the exact phone number on file.
        Order registeredOrder = Order.builder()
                .id(2L)
                .orderNumber("F21-REG-1")
                .user(User.builder().id(5L).email("real.customer@example.com").build())
                .shippingPhone("9998887777")
                .build();

        when(orderRepository.findByOrderNumber("F21-REG-1")).thenReturn(Optional.of(registeredOrder));

        assertThatThrownBy(() -> guestOrderService.trackOrder(
                GuestOrderTrackingRequest.builder().orderNumber("F21-REG-1").phone("9998887777").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void trackOrder_phoneWithFormattingDifferences_stillMatches() {

        when(orderRepository.findByOrderNumber("F21-GUEST-1")).thenReturn(Optional.of(guestOrder));
        when(orderMapper.toResponse(guestOrder)).thenReturn(OrderResponse.builder().id(1L).build());

        // Stored as "9998887777" -- submitted with spaces/dashes/parens.
        OrderResponse response = guestOrderService.trackOrder(
                GuestOrderTrackingRequest.builder().orderNumber("F21-GUEST-1").phone("(999) 888-7777").build());

        assertThat(response).isNotNull();
    }

    @Test
    void trackOrder_missingCountryCodeDoesNotAccidentallyMatchPresentOne() {

        Order orderWithCountryCode = Order.builder()
                .id(3L)
                .orderNumber("F21-GUEST-2")
                .user(null)
                .guestEmail("guest2@example.com")
                .shippingPhone("+919998887777")
                .build();

        when(orderRepository.findByOrderNumber("F21-GUEST-2")).thenReturn(Optional.of(orderWithCountryCode));

        assertThatThrownBy(() -> guestOrderService.trackOrder(
                GuestOrderTrackingRequest.builder().orderNumber("F21-GUEST-2").phone("9998887777").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getInvoicePdf_correctPhone_returnsGeneratedPdf() {

        when(orderRepository.findByOrderNumber("F21-GUEST-1")).thenReturn(Optional.of(guestOrder));
        when(invoiceService.generateInvoicePdf(guestOrder)).thenReturn(new byte[] { 1, 2, 3 });

        byte[] pdf = guestOrderService.getInvoicePdf("F21-GUEST-1", "9998887777");

        assertThat(pdf).containsExactly(1, 2, 3);
    }

    @Test
    void getInvoicePdf_wrongPhone_throwsNotFound() {

        when(orderRepository.findByOrderNumber("F21-GUEST-1")).thenReturn(Optional.of(guestOrder));

        assertThatThrownBy(() -> guestOrderService.getInvoicePdf("F21-GUEST-1", "0000000000"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
