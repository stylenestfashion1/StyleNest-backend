package com.stylenest.stylenest_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.*;

/**
 * One rental lehenga inside a {@link RentalCatalog}. Deliberately flat and
 * standalone -- unlike retail {@link Product}, there is no color/size
 * variant matrix, no stock/inventory tracking, and no relationship to the
 * retail catalog at all. rentalPrice is independent of, and never derived
 * from, Product.price.
 */
@Entity
@Table(name = "rental_catalog_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalCatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private RentalCatalog catalog;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String colour;

    @Column(nullable = false)
    private BigDecimal rentalPrice;

    @Builder.Default
    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Builder.Default
    @OneToMany(
            mappedBy = "item",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<RentalCatalogItemImage> images = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
