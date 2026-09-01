package com.stylenest.stylenest_backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.OrderItem;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.Color;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;
import com.stylenest.stylenest_backend.enums.Size;
import com.stylenest.stylenest_backend.repository.ShipmentRepository;

class OrderMapperTest {

    private final ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
    private final OrderMapper orderMapper = new OrderMapper(shipmentRepository);

    @BeforeEach
    void setUp() {
        when(shipmentRepository.findByOrder(any(Order.class))).thenReturn(Optional.empty());
    }

    @Test
    void toResponse_populatesProductIdFromVariantProductRelationship() {

        Product product = Product.builder().id(42L).name("Rose Wrap Midi Dress").build();

        ProductVariant variant = ProductVariant.builder()
                .id(7L)
                .product(product)
                .color(Color.BLACK)
                .size(Size.M)
                .stock(5)
                .build();

        OrderItem item = OrderItem.builder()
                .productVariant(variant)
                .quantity(2)
                .price(BigDecimal.valueOf(2799))
                .build();

        Order order = Order.builder()
                .id(1L)
                .orderNumber("F21-TEST-1")
                .totalAmount(BigDecimal.valueOf(5598))
                .paymentMethod(PaymentMethod.COD)
                .orderItems(List.of(item))
                .build();

        OrderResponse response = orderMapper.toResponse(order);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(42L);
        assertThat(response.getItems().get(0).getProductVariantId()).isEqualTo(7L);
    }

    @Test
    void toResponse_registeredOrder_isGuestIsFalse() {

        User user = User.builder().id(1L).email("customer@example.com").build();

        Order order = Order.builder()
                .id(1L)
                .orderNumber("F21-TEST-2")
                .totalAmount(BigDecimal.TEN)
                .paymentMethod(PaymentMethod.COD)
                .user(user)
                .orderItems(List.of())
                .build();

        assertThat(orderMapper.toResponse(order).getIsGuest()).isFalse();
    }

    @Test
    void toResponse_guestOrder_isGuestIsTrueAndUsesShippingSnapshotNotAddress() {

        Order order = Order.builder()
                .id(2L)
                .orderNumber("F21-TEST-3")
                .totalAmount(BigDecimal.TEN)
                .paymentMethod(PaymentMethod.COD)
                .user(null)
                .guestEmail("guest@example.com")
                .shippingFullName("Guest Customer")
                .shippingCity("Metropolis")
                .orderItems(List.of())
                .build();

        OrderResponse response = orderMapper.toResponse(order);

        assertThat(response.getIsGuest()).isTrue();
        assertThat(response.getShippingAddress().getFullName()).isEqualTo("Guest Customer");
        assertThat(response.getShippingAddress().getCity()).isEqualTo("Metropolis");
    }

    @Test
    void toResponse_includesShipmentStatusAndTrackingWhenShipmentExists() {

        Order order = Order.builder()
                .id(3L)
                .orderNumber("F21-TEST-4")
                .totalAmount(BigDecimal.TEN)
                .paymentMethod(PaymentMethod.COD)
                .orderItems(List.of())
                .build();

        Shipment shipment = Shipment.builder()
                .id(1L)
                .order(order)
                .shipmentStatus(ShipmentStatus.SHIPPED)
                .trackingNumber("TRK123")
                .courierName("Test Courier")
                .build();

        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.of(shipment));

        OrderResponse response = orderMapper.toResponse(order);

        assertThat(response.getShipmentStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(response.getTrackingNumber()).isEqualTo("TRK123");
        assertThat(response.getCourierName()).isEqualTo("Test Courier");
    }
}
