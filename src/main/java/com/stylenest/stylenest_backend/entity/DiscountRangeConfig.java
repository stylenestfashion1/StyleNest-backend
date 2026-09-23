package com.stylenest.stylenest_backend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.*;

/**
 * The admin-editable "QR Display Range" -- ONLY the min/max text shown on
 * the printed QR poster (e.g. "15% - 40% OFF"). This is a purely cosmetic,
 * fully independent setting: it has NO effect on the real discount a
 * customer can receive, which is entirely driven by DiscountSlot's weighted
 * probabilities (see DiscountOfferServiceImpl#selectWeightedDiscount).
 * Always exactly one row (id = SINGLETON_ID). Bounded only by
 * ABSOLUTE_FLOOR/ABSOLUTE_CEILING (1-100%) in DiscountConfigServiceImpl --
 * deliberately NOT validated against, or auto-adjusted by, DiscountSlot in
 * any way. Managed at Admin -> Rewards -> Show Discount QR.
 */
@Entity
@Table(name = "discount_range_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountRangeConfig {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private Integer minDiscountPercentage;

    @Column(nullable = false)
    private Integer maxDiscountPercentage;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
