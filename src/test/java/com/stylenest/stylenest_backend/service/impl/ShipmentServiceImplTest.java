package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stylenest.stylenest_backend.dto.shipment.DtdcBookingRequest;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentResponse;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentUpdateRequest;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.DtdcApiException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.ShipmentMapper;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ShipmentHistoryRepository;
import com.stylenest.stylenest_backend.repository.ShipmentRepository;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.courier.CourierTrackingService;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentHistoryRepository shipmentHistoryRepository;

    @Mock
    private CourierTrackingService courierTrackingService;

    @Mock
    private EmailService emailService;

    private ShipmentServiceImpl shipmentService;

    private Order order;
    private Shipment shipment;

    @BeforeEach
    void setUp() {

        shipmentService = new ShipmentServiceImpl(
                orderRepository, shipmentRepository, shipmentHistoryRepository, new ShipmentMapper(), courierTrackingService, emailService);

        order = Order.builder()
                .id(1L)
                .orderNumber("SN-1")
                .paymentMethod(PaymentMethod.COD)
                .totalAmount(new BigDecimal("1500.00"))
                .shippingFullName("Test Customer")
                .shippingPhone("9999999999")
                .shippingAddressLine1("Line 1")
                .shippingCity("Indore")
                .shippingState("Madhya Pradesh")
                .shippingPostalCode("452010")
                .shippingCountryCode("IN")
                .build();

        shipment = Shipment.builder().id(1L).order(order).shipmentStatus(ShipmentStatus.PROCESSING).build();

        lenient().when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        lenient().when(shipmentRepository.findByOrder(order)).thenReturn(Optional.of(shipment));
        lenient().when(shipmentRepository.findByOrder_Id(1L)).thenReturn(Optional.of(shipment));
        lenient().when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(shipmentHistoryRepository.findByShipmentOrderByTimestampAsc(shipment)).thenReturn(List.of());
        lenient().when(shipmentHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Stream<Arguments> validForwardTransitions() {
        return Stream.of(
                Arguments.of(ShipmentStatus.PROCESSING, ShipmentStatus.PACKED),
                Arguments.of(ShipmentStatus.PROCESSING, ShipmentStatus.SHIPPED), // skip-ahead allowed
                Arguments.of(ShipmentStatus.PACKED, ShipmentStatus.OUT_FOR_DELIVERY),
                Arguments.of(ShipmentStatus.SHIPPED, ShipmentStatus.DELIVERED),
                Arguments.of(ShipmentStatus.PROCESSING, ShipmentStatus.CANCELLED),
                Arguments.of(ShipmentStatus.SHIPPED, ShipmentStatus.RETURNED)
        );
    }

    @ParameterizedTest
    @MethodSource("validForwardTransitions")
    void updateShipment_validTransition_succeeds(ShipmentStatus from, ShipmentStatus to) {

        shipment.setShipmentStatus(from);

        ShipmentResponse response = shipmentService.updateShipment(
                1L, ShipmentUpdateRequest.builder().shipmentStatus(to).build());

        assertThat(response.getShipmentStatus()).isEqualTo(to);
    }

    private static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(ShipmentStatus.SHIPPED, ShipmentStatus.PACKED), // backward
                Arguments.of(ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.PROCESSING), // backward
                Arguments.of(ShipmentStatus.DELIVERED, ShipmentStatus.SHIPPED), // terminal
                Arguments.of(ShipmentStatus.CANCELLED, ShipmentStatus.PROCESSING), // terminal
                Arguments.of(ShipmentStatus.RETURNED, ShipmentStatus.DELIVERED) // terminal
        );
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void updateShipment_invalidTransition_rejected(ShipmentStatus from, ShipmentStatus to) {

        shipment.setShipmentStatus(from);

        assertThatThrownBy(() -> shipmentService.updateShipment(
                1L, ShipmentUpdateRequest.builder().shipmentStatus(to).build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateShipment_sameStatusAgain_allowsUpdatingTrackingInfoOnly() {

        shipment.setShipmentStatus(ShipmentStatus.PACKED);

        ShipmentResponse response = shipmentService.updateShipment(1L, ShipmentUpdateRequest.builder()
                .shipmentStatus(ShipmentStatus.PACKED)
                .trackingNumber("TRK999")
                .build());

        assertThat(response.getShipmentStatus()).isEqualTo(ShipmentStatus.PACKED);
        assertThat(response.getTrackingNumber()).isEqualTo("TRK999");
    }

    @Test
    void updateShipment_toShipped_setsShippedAt() {

        shipment.setShipmentStatus(ShipmentStatus.PACKED);

        shipmentService.updateShipment(1L, ShipmentUpdateRequest.builder()
                .shipmentStatus(ShipmentStatus.SHIPPED)
                .build());

        assertThat(shipment.getShippedAt()).isNotNull();
    }

    @Test
    void updateShipment_toDelivered_setsDeliveredAt() {

        shipment.setShipmentStatus(ShipmentStatus.OUT_FOR_DELIVERY);

        shipmentService.updateShipment(1L, ShipmentUpdateRequest.builder()
                .shipmentStatus(ShipmentStatus.DELIVERED)
                .build());

        assertThat(shipment.getDeliveredAt()).isNotNull();
    }

    @Test
    void updateShipment_orderWithNoShipmentYet_throwsNotFound() {

        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shipmentService.updateShipment(
                1L, ShipmentUpdateRequest.builder().shipmentStatus(ShipmentStatus.PACKED).build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateShipment_unknownOrder_throwsNotFound() {

        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shipmentService.updateShipment(
                404L, ShipmentUpdateRequest.builder().shipmentStatus(ShipmentStatus.PACKED).build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- DTDC booking ----

    private DtdcBookingRequest bookingRequest() {
        return DtdcBookingRequest.builder()
                .weightKg(new BigDecimal("1.0"))
                .lengthCm(new BigDecimal("30"))
                .widthCm(new BigDecimal("20"))
                .heightCm(new BigDecimal("10"))
                .numPieces(1)
                .build();
    }

    @Test
    void bookDtdcShipment_codOrder_sendsCodAmountAndMarksBooked() {

        when(courierTrackingService.bookShipment(any()))
                .thenReturn(new CourierTrackingService.BookingResult("7X100761088"));

        ShipmentResponse response = shipmentService.bookDtdcShipment(1L, bookingRequest());

        assertThat(response.getTrackingNumber()).isEqualTo("7X100761088");
        assertThat(response.getCourierName()).isEqualTo("DTDC");
        assertThat(response.getShipmentStatus()).isEqualTo(ShipmentStatus.PACKED);

        var captor = org.mockito.ArgumentCaptor.forClass(CourierTrackingService.ShipmentBookingRequest.class);
        verify(courierTrackingService).bookShipment(captor.capture());

        assertThat(captor.getValue().cashOnDelivery()).isTrue();
        assertThat(captor.getValue().codAmount()).isEqualByComparingTo("1500.00");
        assertThat(captor.getValue().declaredValue()).isEqualByComparingTo("1500.00");
        assertThat(captor.getValue().customerReferenceNumber()).isEqualTo("SN-1");
    }

    @Test
    void bookDtdcShipment_prepaidOrder_sendsNoCodAmount() {

        order.setPaymentMethod(PaymentMethod.ONLINE);

        when(courierTrackingService.bookShipment(any()))
                .thenReturn(new CourierTrackingService.BookingResult("7X100761099"));

        shipmentService.bookDtdcShipment(1L, bookingRequest());

        var captor = org.mockito.ArgumentCaptor.forClass(CourierTrackingService.ShipmentBookingRequest.class);
        verify(courierTrackingService).bookShipment(captor.capture());

        assertThat(captor.getValue().cashOnDelivery()).isFalse();
        assertThat(captor.getValue().codAmount()).isNull();
    }

    @Test
    void bookDtdcShipment_alreadyHasTrackingNumber_rejectedWithoutCallingDtdc() {

        shipment.setTrackingNumber("EXISTING123");

        assertThatThrownBy(() -> shipmentService.bookDtdcShipment(1L, bookingRequest()))
                .isInstanceOf(BadRequestException.class);

        verify(courierTrackingService, never()).bookShipment(any());
    }

    @Test
    void bookDtdcShipment_calledTwiceInARow_secondCallRejected() {

        when(courierTrackingService.bookShipment(any()))
                .thenReturn(new CourierTrackingService.BookingResult("7X100761088"));

        shipmentService.bookDtdcShipment(1L, bookingRequest());

        // shipment.trackingNumber is now set on the same in-memory object
        // shipmentRepository.save() returned -- a second call must see it
        // and refuse, simulating a double-click/retry.
        assertThatThrownBy(() -> shipmentService.bookDtdcShipment(1L, bookingRequest()))
                .isInstanceOf(BadRequestException.class);

        verify(courierTrackingService, org.mockito.Mockito.times(1)).bookShipment(any());
    }

    @Test
    void bookDtdcShipment_nonIndiaOrder_rejectedWithoutCallingDtdc() {

        order.setShippingCountryCode("US");

        assertThatThrownBy(() -> shipmentService.bookDtdcShipment(1L, bookingRequest()))
                .isInstanceOf(BadRequestException.class);

        verify(courierTrackingService, never()).bookShipment(any());
    }

    @Test
    void bookDtdcShipment_cancelledShipment_rejected() {

        shipment.setShipmentStatus(ShipmentStatus.CANCELLED);

        assertThatThrownBy(() -> shipmentService.bookDtdcShipment(1L, bookingRequest()))
                .isInstanceOf(BadRequestException.class);

        verify(courierTrackingService, never()).bookShipment(any());
    }

    @Test
    void bookDtdcShipment_dtdcApiFailure_propagatesAndLeavesShipmentUntouched() {

        when(courierTrackingService.bookShipment(any()))
                .thenThrow(new DtdcApiException("DTDC could not book this shipment: pincode not serviceable."));

        assertThatThrownBy(() -> shipmentService.bookDtdcShipment(1L, bookingRequest()))
                .isInstanceOf(DtdcApiException.class);

        assertThat(shipment.getTrackingNumber()).isNull();
    }

    // ---- DTDC cancellation ----

    @Test
    void cancelDtdcShipment_bookedShipment_cancelsAndRecordsHistory() {

        shipment.setTrackingNumber("7X100761088");
        shipment.setCourierName("DTDC");
        shipment.setShipmentStatus(ShipmentStatus.PACKED);

        ShipmentResponse response = shipmentService.cancelDtdcShipment(1L);

        assertThat(response.getShipmentStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        verify(courierTrackingService).cancelShipment("7X100761088");
    }

    @Test
    void cancelDtdcShipment_notBookedWithDtdc_rejected() {

        assertThatThrownBy(() -> shipmentService.cancelDtdcShipment(1L))
                .isInstanceOf(BadRequestException.class);

        verify(courierTrackingService, never()).cancelShipment(any());
    }

    @Test
    void cancelDtdcShipment_alreadyDelivered_rejected() {

        shipment.setTrackingNumber("7X100761088");
        shipment.setCourierName("DTDC");
        shipment.setShipmentStatus(ShipmentStatus.DELIVERED);

        assertThatThrownBy(() -> shipmentService.cancelDtdcShipment(1L))
                .isInstanceOf(BadRequestException.class);

        verify(courierTrackingService, never()).cancelShipment(any());
    }

    // ---- DTDC label ----

    @Test
    void fetchDtdcLabel_bookedShipment_returnsPdfBytes() {

        shipment.setTrackingNumber("7X100761088");
        shipment.setCourierName("DTDC");

        byte[] pdf = {1, 2, 3};
        when(courierTrackingService.fetchLabel("7X100761088")).thenReturn(pdf);

        assertThat(shipmentService.fetchDtdcLabel(1L)).isEqualTo(pdf);
    }

    @Test
    void fetchDtdcLabel_notBookedWithDtdc_rejected() {

        assertThatThrownBy(() -> shipmentService.fetchDtdcLabel(1L))
                .isInstanceOf(BadRequestException.class);
    }

    // ---- DTDC tracking refresh ----

    @Test
    void refreshDtdcTracking_statusChanged_updatesShipmentAndAddsHistory() {

        shipment.setTrackingNumber("7X100761088");
        shipment.setCourierName("DTDC");
        shipment.setShipmentStatus(ShipmentStatus.PACKED);

        when(courierTrackingService.fetchTracking("DTDC", "7X100761088"))
                .thenReturn(Optional.of(new CourierTrackingService.CourierTrackingSnapshot(
                        ShipmentStatus.IN_TRANSIT, "GHAZIABAD APEX", LocalDateTime.now(), "Picked Up")));

        ShipmentResponse response = shipmentService.refreshDtdcTracking(1L);

        assertThat(response.getShipmentStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        verify(shipmentHistoryRepository).save(any());
    }

    @Test
    void refreshDtdcTracking_statusUnchanged_doesNotDuplicateHistory() {

        shipment.setTrackingNumber("7X100761088");
        shipment.setCourierName("DTDC");
        shipment.setShipmentStatus(ShipmentStatus.IN_TRANSIT);

        when(courierTrackingService.fetchTracking("DTDC", "7X100761088"))
                .thenReturn(Optional.of(new CourierTrackingService.CourierTrackingSnapshot(
                        ShipmentStatus.IN_TRANSIT, "HUB", LocalDateTime.now(), "Heldup")));

        shipmentService.refreshDtdcTracking(1L);

        verify(shipmentHistoryRepository, never()).save(any());
    }

    @Test
    void refreshDtdcTracking_alreadyDelivered_neverDowngraded() {

        shipment.setTrackingNumber("7X100761088");
        shipment.setCourierName("DTDC");
        shipment.setShipmentStatus(ShipmentStatus.DELIVERED);

        when(courierTrackingService.fetchTracking("DTDC", "7X100761088"))
                .thenReturn(Optional.of(new CourierTrackingService.CourierTrackingSnapshot(
                        ShipmentStatus.IN_TRANSIT, "HUB", LocalDateTime.now(), "Heldup")));

        ShipmentResponse response = shipmentService.refreshDtdcTracking(1L);

        assertThat(response.getShipmentStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        verify(shipmentHistoryRepository, never()).save(any());
    }

    @Test
    void refreshDtdcTracking_notBookedWithDtdc_rejected() {

        assertThatThrownBy(() -> shipmentService.refreshDtdcTracking(1L))
                .isInstanceOf(BadRequestException.class);

        verify(courierTrackingService, never()).fetchTracking(any(), any());
    }
}
