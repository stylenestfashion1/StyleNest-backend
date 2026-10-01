package com.stylenest.stylenest_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.RentalBooking;
import com.stylenest.stylenest_backend.enums.RentalBookingStatus;

public interface RentalBookingRepository extends JpaRepository<RentalBooking, Long> {

    Optional<RentalBooking> findByBookingReference(String bookingReference);

    boolean existsByBookingReference(String bookingReference);

    List<RentalBooking> findAllByOrderByCreatedAtDesc();

    // Used by RentalBookingExpiryScheduler to sweep abandoned holds -- see
    // RentalSettings.pendingPaymentExpiryMinutes.
    List<RentalBooking> findAllByStatusAndCreatedAtBefore(RentalBookingStatus status, LocalDateTime cutoff);
}
