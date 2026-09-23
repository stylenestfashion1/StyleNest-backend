package com.stylenest.stylenest_backend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.*;

/**
 * One admin-configurable in-store QR discount value + its selection weight
 * (see DiscountSlotSeeder for the default seed, DiscountConfigServiceImpl
 * for validation, DiscountOfferServiceImpl for the weighted-random
 * selection over these). The number of slots is fully dynamic -- admins add
 * and remove rows via Admin -> Rewards -> Custom Discount, between 2 and 10
 * at a time (DiscountConfigServiceImpl.MIN_SLOTS/MAX_SLOTS) -- so there is
 * deliberately no fixed slot number/position here, only a unique discount
 * value. Every config update replaces the full slot set in one transaction
 * (see DiscountConfigServiceImpl.updateConfig).
 */
@Entity
@Table(name = "discount_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer discountPercentage;

    @Column(nullable = false)
    private Integer probabilityPercentage;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
