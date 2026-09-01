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

    @Column(unique = true)
    private String sku;

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

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    
}