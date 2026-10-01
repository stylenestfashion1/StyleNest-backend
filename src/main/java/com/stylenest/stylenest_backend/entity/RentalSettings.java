package com.stylenest.stylenest_backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.*;

/**
 * Always exactly one row (id = SINGLETON_ID), same pattern as
 * {@link DiscountRangeConfig}. Everything here is display/config text or
 * campaign-wide dates shared by the whole temporary rental module -- never
 * per-catalog, since there's one Navratri season, one shop WhatsApp
 * number, one payment QR at a time. Admin -> Rental (Navratri) -> Rental
 * Settings.
 */
@Entity
@Table(name = "rental_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private LocalDate seasonStartDate;

    @Column(nullable = false)
    private LocalDate seasonEndDate;

    @Column(nullable = false)
    private String whatsappNumber;

    private String paymentQrImageUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pickupInstructions;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String returnInstructions;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String lateReturnMessage;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String paymentInstructions;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String termsAndConditions;

    // How long an unpaid PENDING_PAYMENT booking holds its dates before
    // RentalBookingExpiryScheduler (and the availability check itself,
    // belt-and-suspenders) treats it as abandoned. See section 15/16 of
    // the spec -- an abandoned hold must never permanently block a date.
    @Column(nullable = false)
    private Integer pendingPaymentExpiryMinutes;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
