package com.stylenest.stylenest_backend.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.order.OrderItemResponse;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.dto.order.OrderSummaryResponse;
import com.stylenest.stylenest_backend.dto.order.ShippingAddressSnapshotResponse;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.OrderItem;
import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.repository.ShipmentRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final ShipmentRepository shipmentRepository;

    public OrderResponse toResponse(Order order) {

        List<OrderItemResponse> items = order.getOrderItems()
                .stream()
                .map(this::toItemResponse)
                .toList();

        // Single-order detail calls only -- negligible extra query, and
        // toSummaryResponse (used by list endpoints) deliberately skips
        // this to avoid N+1 there.
        Shipment shipment = shipmentRepository.findByOrder(order).orElse(null);

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .createdAt(order.getCreatedAt())
                .items(items)
                .isGuest(order.getUser() == null)
                .shippingAddress(toShippingSnapshot(order))
                .shipmentStatus(shipment == null ? null : shipment.getShipmentStatus())
                .trackingNumber(shipment == null ? null : shipment.getTrackingNumber())
                .courierName(shipment == null ? null : shipment.getCourierName())
                .estimatedDeliveryDate(shipment == null ? null : shipment.getEstimatedDeliveryDate())
                .invoiceAvailable(true)
                .build();
    }

    public OrderSummaryResponse toSummaryResponse(Order order) {

        return OrderSummaryResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .totalAmount(order.getTotalAmount())
                .orderStatus(order.getOrderStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private ShippingAddressSnapshotResponse toShippingSnapshot(Order order) {

        return ShippingAddressSnapshotResponse.builder()
                .fullName(order.getShippingFullName())
                .phone(order.getShippingPhone())
                .phoneCountryCode(order.getShippingPhoneCountryCode())
                .addressLine1(order.getShippingAddressLine1())
                .addressLine2(order.getShippingAddressLine2())
                .city(order.getShippingCity())
                .state(order.getShippingState())
                .postalCode(order.getShippingPostalCode())
                .country(order.getShippingCountry())
                .countryCode(order.getShippingCountryCode())
                .build();
    }

    private OrderItemResponse toItemResponse(OrderItem item) {

        BigDecimal subtotal = item.getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));

        return OrderItemResponse.builder()
                .productId(item.getProductVariant().getProduct().getId())
                .productVariantId(item.getProductVariant().getId())
                .productName(item.getProductVariant().getProduct().getName())
                .color(item.getProductVariant().getColor().name())
                .size(item.getProductVariant().getSize().name())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(subtotal)
                .build();
    }

}
