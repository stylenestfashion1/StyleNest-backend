package com.stylenest.stylenest_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private Integer displayOrder;

    // Images belong to a (product, color) pair, not an individual size
    // variant -- every size of the same color shares one photo set, since
    // the garment photographed is identical regardless of size. The old
    // variant_id column is kept in the DB as a migration safety net but is
    // no longer read or written here.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // Free-form color name, matching the owning color group's
    // ProductVariant.color exactly (same trimmed+uppercase normalization).
    @Column(nullable = false, length = 50)
    private String color;
}