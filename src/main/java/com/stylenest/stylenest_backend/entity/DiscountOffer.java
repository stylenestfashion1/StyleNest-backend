package com.stylenest.stylenest_backend.entity;

import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.DiscountOfferStatus;

import jakarta.persistence.*;
import lombok.*;

/**
 * One customer's in-store QR discount claim. discountPercentage is a
 * SNAPSHOT of whatever DiscountSlot was randomly selected at the moment of
 * generation -- never recomputed from the current DiscountSlot config, so
 * an admin later changing the slot percentages can never alter a
 * already-generated customer's discount (see DiscountOfferServiceImpl).
 *
 * mobileNumber carries a DB-level unique constraint -- one mobile number
 * may claim exactly one discount, ever. The unique constraint (not just an
 * application-level existence check) is what actually prevents two
 * concurrent requests for the same number both succeeding; see
 * DiscountOfferServiceImpl.claim for how the resulting
 * DataIntegrityViolationException is turned into a clean user-facing error.
 */
@Entity
@Table(name = "discount_offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false, unique = true)
    private String mobileNumber;

    @Column(nullable = false)
    private Integer discountPercentage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountOfferStatus status;

    @Column(nullable = false)
    private LocalDateTime generatedAt;
}
