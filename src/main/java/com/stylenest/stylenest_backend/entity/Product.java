package com.stylenest.stylenest_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String productimageUrl;

    
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    
    private BigDecimal discountPrice;

    private String shortDescription;

    private String fabric;

    // Short, stable product code (e.g. "UGT" for "Urban Graphic Tee"),
    // generated once at creation time -- see SkuGenerator. Every variant's
    // full SKU is built from this plus its color and size (e.g.
    // "STN-UGT-BLK-M"), so this must never change after variants exist.
    @Column(unique = true)
    private String sku;

    // HSN code for invoicing -- deliberately nullable with no fabricated
    // default; a real value must come from the business (see
    // InvoiceGenerationServiceImpl for how a missing value is handled).
    private String hsnCode;

    // Admin-only internal identification code (originally requested for
    // Jeans, but not restricted to that category -- see ProductRequest).
    // Nullable/optional, admin-entered only, never auto-generated. Unique
    // when present so it reliably identifies one product; MySQL allows
    // multiple NULLs under a UNIQUE constraint, so products without a code
    // (the common case) never collide with each other. Deliberately
    // EXCLUDED from ProductResponse/ProductMapper.toResponse -- that DTO
    // is shared by the public, unauthenticated GET /api/products/** and
    // ADMIN endpoints alike, so anything added there is customer-visible.
    // Read only via the admin-only ProductJeansCodeResponse endpoints
    // (see AdminProductController) instead.
    @Column(name = "jeans_code", unique = true, length = 64)
    private String jeansCode;

    @Column(columnDefinition = "TEXT")
    private String careInstructions;

    @Builder.Default
    private Boolean featured = false;

    // Independent of both featured and Sale (discountPrice) -- a product
    // can be any combination of the three. NOT NULL DEFAULT false at the
    // DB level (unlike featured/active above, which are only Java-side
    // defaults) since Trending is meant to be a real, always-present flag.
    @Column(nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean trending = false;

    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

     

    @OneToMany(
            mappedBy = "product",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY

    )@Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    // Per-currency pricing (see ProductPrice). The INR row is a read-only
    // mirror of price/discountPrice above, kept in sync by ProductServiceImpl
    // -- this list is never independently written to for INR. USD (and any
    // future currency) rows here are the sole source of truth for that
    // market, admin-set only.
    @OneToMany(
            mappedBy = "product",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<ProductPrice> prices = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    
}