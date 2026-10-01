package com.stylenest.stylenest_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.*;

/**
 * A wholesale-catalog product. Deliberately flat (no color/size variants
 * like the retail {@link Product}/{@link ProductVariant} pair) -- bulk
 * listings here are a single line item per product, matching how the shop
 * actually sells wholesale ("100 pieces of this T-shirt"), and never
 * automatically appear in the retail catalog or vice versa.
 */
@Entity
@Table(name = "bulk_products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String imageUrl;

    @Column(nullable = false)
    private BigDecimal price;

    // HSN code for invoicing -- deliberately nullable, never fabricated.
    // See InvoiceGenerationServiceImpl.
    private String hsnCode;

    // The quantity the admin has configured this product to be ordered in
    // bulk (e.g. 100). Shown to the customer as the default/minimum order
    // quantity; enforced server-side at order time in BulkOrderServiceImpl.
    @Column(nullable = false)
    private Integer minOrderQuantity;

    // Total pieces the shop currently has available for wholesale of this
    // item. Nullable: an admin can leave this unset to mean "no cap
    // tracked here, handled manually" -- when present it's enforced as a
    // hard ceiling at order time.
    private Integer availableStock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bulk_category_id", nullable = false)
    private BulkCategory category;

    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
