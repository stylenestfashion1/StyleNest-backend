package com.stylenest.stylenest_backend.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.entity.BulkOrder;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.repository.BulkOrderRepository;
import com.stylenest.stylenest_backend.repository.BulkProductRepository;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Automatically cleans up abandoned orders where user initiated checkout but never completed payment
 * (e.g. closed browser tab without clicking cancel on gateway).
 * Runs every 10 minutes and expires orders older than 30 minutes, releasing held inventory.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbandonedOrderExpiryScheduler {

    private final OrderRepository orderRepository;
    private final BulkOrderRepository bulkOrderRepository;
    private final ProductVariantRepository productVariantRepository;
    private final BulkProductRepository bulkProductRepository;

    private static final int EXPIRY_MINUTES = 30;

    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    public void expireAbandonedOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(EXPIRY_MINUTES);

        // 1. Expire retail orders
        List<Order> staleRetailOrders = orderRepository.findByOrderStatusAndPaymentStatusAndCreatedAtBefore(
                OrderStatus.PENDING, PaymentStatus.PENDING, cutoff);

        for (Order order : staleRetailOrders) {
            try {
                order.setOrderStatus(OrderStatus.CANCELLED);
                order.setPaymentStatus(PaymentStatus.FAILED);

                if (order.getOrderItems() != null) {
                    for (var item : order.getOrderItems()) {
                        if (item.getProductVariant() != null) {
                            var variant = item.getProductVariant();
                            variant.setStock(variant.getStock() + item.getQuantity());
                            productVariantRepository.save(variant);
                        }
                    }
                }
                orderRepository.save(order);
                log.info("Auto-expired abandoned retail order {} (created: {}) and restored stock",
                        order.getOrderNumber(), order.getCreatedAt());
            } catch (Exception e) {
                log.error("Failed to auto-expire retail order {}: {}", order.getOrderNumber(), e.getMessage());
            }
        }

        // 2. Expire wholesale bulk orders
        List<BulkOrder> staleBulkOrders = bulkOrderRepository.findByOrderStatusAndPaymentStatusAndCreatedAtBefore(
                OrderStatus.PENDING, PaymentStatus.PENDING, cutoff);

        for (BulkOrder bulkOrder : staleBulkOrders) {
            try {
                bulkOrder.setOrderStatus(OrderStatus.CANCELLED);
                bulkOrder.setPaymentStatus(PaymentStatus.FAILED);

                if (bulkOrder.getItems() != null) {
                    for (var item : bulkOrder.getItems()) {
                        if (item.getBulkProduct() != null && item.getBulkProduct().getAvailableStock() != null) {
                            var prod = item.getBulkProduct();
                            prod.setAvailableStock(prod.getAvailableStock() + item.getQuantity());
                            bulkProductRepository.save(prod);
                        }
                    }
                }
                bulkOrderRepository.save(bulkOrder);
                log.info("Auto-expired abandoned bulk order {} (created: {}) and restored stock",
                        bulkOrder.getBulkOrderNumber(), bulkOrder.getCreatedAt());
            } catch (Exception e) {
                log.error("Failed to auto-expire bulk order {}: {}", bulkOrder.getBulkOrderNumber(), e.getMessage());
            }
        }
    }
}
