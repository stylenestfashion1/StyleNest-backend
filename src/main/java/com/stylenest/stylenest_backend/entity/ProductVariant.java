package com.stylenest.stylenest_backend.entity;

import com.stylenest.stylenest_backend.enums.Size;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "product_variants",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {
                "product_id",
                "color",
                "size"
            }
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Free-form color name (e.g. "BLACK", "DUSTY ROSE") -- admins can set
    // any value, not just a fixed list. Always stored trimmed+uppercased
    // (see ProductVariantServiceImpl) so a casing typo can't silently
    // split what should be one color into two separate groups.
    @Column(nullable = false, length = 50)
    private String color;

    // Optional exact shade (e.g. "#A9C6E8") for this specific variant,
    // sampled by the admin from the actual product photo. `color` above
    // stays the coarse name used for filtering/grouping ("Blue" groups
    // every blue shade together); this is only ever used to render the
    // swatch itself so it visually matches the real garment instead of a
    // flat, generic CSS color guessed from the name. Null falls back to a
    // curated hex for known names in the frontend (see swatchColor.js).
    private String colorHex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Size size;

    @Column(nullable = false)
    private Integer stock;

    // Deterministic, human-readable variant SKU (e.g. "STN-UGT-BLK-M"),
    // generated once at creation time from the parent product's sku
    // prefix + this variant's color + size -- see SkuGenerator. Never
    // regenerated on update (color/size edits do not rewrite it) so it
    // stays stable for any order/invoice that already references it.
    @Column(unique = true, length = 64)
    private String sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Physical shipping & packaging master data (grams and centimeters).
    // Nullable -- existing variants work without these values until admin populates them.
    // Used for automatic DTDC shipment booking.
    @Column(name = "shipping_weight_grams")
    private java.math.BigDecimal shippingWeightGrams;

    @Column(name = "package_length_cm")
    private java.math.BigDecimal packageLengthCm;

    @Column(name = "package_width_cm")
    private java.math.BigDecimal packageWidthCm;

    @Column(name = "package_height_cm")
    private java.math.BigDecimal packageHeightCm;
}