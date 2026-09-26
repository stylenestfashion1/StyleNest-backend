package com.stylenest.stylenest_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.stylenest.stylenest_backend.enums.Currency;

import jakarta.persistence.*;
import lombok.*;

// One product's price in one currency. INR rows are a read-only mirror of
// Product.price/discountPrice, written only by ProductServiceImpl in the
// same transaction as those columns -- there is exactly one write path for
// INR, so the two can never disagree (see ProductServiceImpl.syncInrPrice).
// USD (and any future currency) rows have no legacy column counterpart --
// this table is their sole source of truth, admin-set only, never derived
// from another currency's price.
@Entity
@Table(
        name = "product_prices",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"product_id", "currency"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Column(nullable = false)
    private BigDecimal regularPrice;

    private BigDecimal discountPrice;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
