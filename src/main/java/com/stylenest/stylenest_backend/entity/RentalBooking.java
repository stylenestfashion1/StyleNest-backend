package com.stylenest.stylenest_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.stylenest.stylenest_backend.enums.RentalBookingStatus;

import jakarta.persistence.*;
import lombok.*;

/**
 * A customer's rental booking for one {@link RentalCatalogItem}, for an
 * inclusive date range. Deliberately its own isolated table -- never a
 * normal ecommerce Order/OrderItem, never tied to a customer account (guest
 * flow only, identified by name + phone), never touched by Cashfree.
 *
 * startDate/endDate/rentalDays/dailyRate/totalAmount here are the
 * ORIGINAL, immutable booking as the customer made it -- never mutated by
 * a later partial cancellation (see {@link RentalBookingSegment}, which is
 * the actual source of truth for "which dates are still blocked"). This
 * keeps the audit trail intact: this row always answers "what did the
 * customer book and pay for", while the segments answer "what's still
 * active right now".
 *
 * dailyRate is a snapshot taken at booking time from
 * RentalCatalogItem.rentalPrice -- if the admin changes the item's price
 * later, this booking's historical amount must never change (see
 * RentalBookingServiceImpl.createBooking).
 */
@Entity
@Table(name = "rental_bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Human-readable, e.g. "RENT-2026-000123" -- generated only AFTER the
    // first save (needs the generated id), then stamped in via a second
    // save, same two-step pattern as Invoice.invoiceNumber. Deliberately
    // NOT nullable=false: the row briefly exists with a null reference
    // between those two saves. Public-facing identifier; the numeric id is
    // never exposed via any public endpoint.
    @Column(unique = true)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private RentalCatalogItem item;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer rentalDays;

    @Column(nullable = false)
    private BigDecimal dailyRate;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RentalBookingStatus status;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerPhone;

    private String transactionId;

    private String paymentScreenshotUrl;

    // Stamped the moment the customer ticks "I Agree & Continue" -- a
    // booking is never created without this being set in the same request
    // (see RentalBookingServiceImpl.createBooking).
    @Column(nullable = false)
    private LocalDateTime termsAcceptedAt;

    @Builder.Default
    @OneToMany(
            mappedBy = "booking",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<RentalBookingSegment> segments = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
