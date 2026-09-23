package com.stylenest.stylenest_backend.mapper;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.bulk.BulkOrderItemResponse;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderResponse;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderSummaryResponse;
import com.stylenest.stylenest_backend.entity.BulkOrder;
import com.stylenest.stylenest_backend.repository.InvoiceRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BulkOrderMapper {

    private final InvoiceRepository invoiceRepository;

    public BulkOrderResponse toResponse(BulkOrder order) {

        return BulkOrderResponse.builder()
                .id(order.getId())
                .bulkOrderNumber(order.getBulkOrderNumber())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .customerPhone(order.getCustomerPhone())
                .shippingAddressLine1(order.getShippingAddressLine1())
                .shippingAddressLine2(order.getShippingAddressLine2())
                .shippingCity(order.getShippingCity())
                .shippingState(order.getShippingState())
                .shippingPostalCode(order.getShippingPostalCode())
                .shippingCountry(order.getShippingCountry())
                .items(order.getItems().stream()
                        .map(item -> BulkOrderItemResponse.builder()
                                .bulkProductId(item.getBulkProduct().getId())
                                .productName(item.getProductNameSnapshot())
                                .imageUrl(item.getBulkProduct().getImageUrl())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .subtotal(item.getSubtotal())
                                .build())
                        .toList())
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .createdAt(order.getCreatedAt())
                .invoiceAvailable(invoiceRepository.existsByBulkOrder(order))
                .build();
    }

    public BulkOrderSummaryResponse toSummaryResponse(BulkOrder order) {

        return BulkOrderSummaryResponse.builder()
                .id(order.getId())
                .bulkOrderNumber(order.getBulkOrderNumber())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .itemCount(order.getItems().size())
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
