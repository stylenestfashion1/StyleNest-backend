package com.stylenest.stylenest_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One photo of a {@link RentalCatalogItem}. Deliberately not a reuse of
 * {@link ProductImage} -- that entity is modeled around a product+color+
 * variant, none of which apply to a rental lehenga. Kept as its own small
 * table so rental image deletion can never be confused with, or cascade
 * into, retail product image deletion.
 */
@Entity
@Table(name = "rental_catalog_item_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalCatalogItemImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private RentalCatalogItem item;

    @Column(nullable = false)
    private String imageUrl;

    @Builder.Default
    @Column(nullable = false)
    private Integer displayOrder = 0;
}
