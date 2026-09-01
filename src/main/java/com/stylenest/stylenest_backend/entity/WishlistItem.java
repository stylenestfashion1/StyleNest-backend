package com.stylenest.stylenest_backend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "wishlist_items",
    uniqueConstraints = {
        // Variant-aware uniqueness: a wishlist can hold one entry per
        // specific variant (e.g. RED/L and BLACK/M of the same product
        // both allowed, as separate rows), plus at most one
        // variant-less "generic" entry per product. A unique index
        // treats each NULL as distinct in both PostgreSQL and MySQL, so
        // this constraint only actively enforces the variant-specific case;
        // the "at most one generic entry per product" rule is enforced
        // in the service layer (see WishlistServiceImpl.addToWishlist).
        @UniqueConstraint(
            columnNames = {
                "wishlist_id",
                "product_variant_id"
            }
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wishlist_id", nullable = false)
    private Wishlist wishlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Optional: the specific variant the customer was viewing when they
     * added this product to their wishlist. Null when unknown (existing
     * behavior) -- additive, nullable column, does not affect any
     * existing row.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = true)
    private ProductVariant productVariant;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}