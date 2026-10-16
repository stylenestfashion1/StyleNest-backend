package com.stylenest.stylenest_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.BulkOrder;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentStatus;

public interface BulkOrderRepository extends JpaRepository<BulkOrder, Long> {

    Optional<BulkOrder> findByBulkOrderNumber(String bulkOrderNumber);

    List<BulkOrder> findAllByOrderByCreatedAtDesc();

    List<BulkOrder> findByOrderStatusAndPaymentStatusAndCreatedAtBefore(OrderStatus orderStatus, PaymentStatus paymentStatus, LocalDateTime cutoff);
}
