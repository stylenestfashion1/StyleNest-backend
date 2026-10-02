package com.stylenest.stylenest_backend.dto.product;

import com.stylenest.stylenest_backend.enums.Size;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantRequest {

    // Free-form -- any color name the admin types, not restricted to a
    // fixed list.
    @NotBlank
    private String color;

    // Optional -- the exact shade for this variant's swatch. Left null,
    // the frontend falls back to a curated color for the `color` enum.
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "colorHex must be a 6-digit hex code, e.g. #A9C6E8")
    private String colorHex;

    @NotNull
    private Size size;

    @NotNull
    @Min(0)
    private Integer stock;

    // Physical shipping & packaging master data (grams and centimeters).
    // Nullable -- optional for variants. If supplied, must be > 0 with sensible upper bounds.
    @jakarta.validation.constraints.DecimalMin(value = "0.01", message = "Shipping weight must be greater than 0")
    @jakarta.validation.constraints.DecimalMax(value = "100000.0", message = "Shipping weight must not exceed 100,000 grams (100 kg)")
    private java.math.BigDecimal shippingWeightGrams;

    @jakarta.validation.constraints.DecimalMin(value = "0.1", message = "Package length must be greater than 0")
    @jakarta.validation.constraints.DecimalMax(value = "500.0", message = "Package length must not exceed 500 cm")
    private java.math.BigDecimal packageLengthCm;

    @jakarta.validation.constraints.DecimalMin(value = "0.1", message = "Package width must be greater than 0")
    @jakarta.validation.constraints.DecimalMax(value = "500.0", message = "Package width must not exceed 500 cm")
    private java.math.BigDecimal packageWidthCm;

    @jakarta.validation.constraints.DecimalMin(value = "0.1", message = "Package height must be greater than 0")
    @jakarta.validation.constraints.DecimalMax(value = "500.0", message = "Package height must not exceed 500 cm")
    private java.math.BigDecimal packageHeightCm;
}