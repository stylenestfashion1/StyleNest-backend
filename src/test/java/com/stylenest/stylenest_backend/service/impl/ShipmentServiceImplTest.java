package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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

import com.stylenest.stylenest_backend.dto.shipment.ShipmentResponse;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentUpdateRequest;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.ShipmentMapper;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ShipmentHistoryRepository;
import com.stylenest.stylenest_backend.repository.ShipmentRepository;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentHistoryRepository shipmentHistoryRepository;

    private ShipmentServiceImpl shipmentService;

    private Order order;
    private Shipment shipment;

    @BeforeEach
    void setUp() {

        shipmentService = new ShipmentServiceImpl(
                orderRepository, shipmentRepository, shipmentHistoryRepository, new ShipmentMapper());

        order = Order.builder().id(1L).build();
        shipment = Shipment.builder().id(1L).order(order).shipmentStatus(ShipmentStatus.PROCESSING).build();

        lenient().when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        lenient().when(shipmentRepository.findByOrder(order)).thenReturn(Optional.of(shipment));
        lenient().when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(shipmentHistoryRepository.findByShipmentOrderByTimestampAsc(shipment)).thenReturn(List.of());
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
}
