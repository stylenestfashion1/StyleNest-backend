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
}