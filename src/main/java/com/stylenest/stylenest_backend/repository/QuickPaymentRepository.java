package com.stylenest.stylenest_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.QuickPayment;

@Repository
public interface QuickPaymentRepository extends JpaRepository<QuickPayment, Long> {

    Optional<QuickPayment> findByPaymentNumber(String paymentNumber);

    List<QuickPayment> findAllByOrderByCreatedAtDesc();

    List<QuickPayment> findByPaymentStatusAndCreatedAtBefore(
            com.stylenest.stylenest_backend.enums.PaymentStatus paymentStatus,
            java.time.LocalDateTime createdAt);
}
