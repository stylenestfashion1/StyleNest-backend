package com.stylenest.stylenest_backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.*;

/**
 * The actual, current source of truth for "which dates does this booking
 * still block" -- see {@link RentalBooking}'s javadoc for why this is
 * separate from the booking's own (immutable, original) startDate/endDate.
 *
 * A booking starts life with exactly one segment covering its full
 * original range, active = true. A full cancellation deactivates every
 * segment belonging to the booking. A PARTIAL cancellation deactivates the
 * one segment that contained the cancelled sub-range and inserts 0-2 new
 * active segments covering whatever remains on either side -- the
 * deactivated segment row is kept forever (never deleted, never had its
 * dates rewritten), so the booking's full history stays reconstructable.
 *
 * Availability/overlap checks (RentalBookingServiceImpl.isRangeAvailable)
 * only ever look at active=true segments whose parent booking is
 * CONFIRMED, PAYMENT_SUBMITTED, or a still-fresh PENDING_PAYMENT -- never
 * at the booking's own startDate/endDate directly.
 */
@Entity
@Table(name = "rental_booking_segments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalBookingSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private RentalBooking booking;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime cancelledAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
