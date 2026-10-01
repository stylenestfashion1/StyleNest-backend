package com.stylenest.stylenest_backend.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.service.RentalBookingService;

import lombok.RequiredArgsConstructor;

/**
 * Sweeps abandoned rental bookings every 5 minutes -- a PENDING_PAYMENT
 * booking older than RentalSettings.pendingPaymentExpiryMinutes gets
 * cancelled and its held dates released (see spec sections 6 and 15: "do
 * not let abandoned unpaid bookings permanently lock rental dates").
 *
 * This is a cleanup/tidiness pass, not the only correctness guarantee --
 * the availability check itself (RentalBookingSegmentRepository's queries)
 * already excludes stale PENDING_PAYMENT holds by the same cutoff
 * independently, so a new booking is never blocked by an abandoned one
 * even in the few minutes between scheduler runs.
 */
@Component
@RequiredArgsConstructor
public class RentalBookingExpiryScheduler {

    private final RentalBookingService rentalBookingService;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void expireAbandonedPendingBookings() {
        rentalBookingService.expireAbandonedPendingBookings();
    }
}
