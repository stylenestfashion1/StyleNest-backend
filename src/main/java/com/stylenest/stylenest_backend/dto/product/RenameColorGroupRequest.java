package com.stylenest.stylenest_backend.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

/**
 * Renames an entire color group on a product in one operation -- every existing size of the
 * current color becomes the new color, preserving each variant's id, stock, and order history.
 * See ProductVariantServiceImpl.renameColorGroup.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenameColorGroupRequest {

    // Free-form, same as ProductVariantRequest.color -- any name the admin types.
    @NotBlank
    private String newColor;

    // Optional -- if provided, sets this exact shade on every variant in the group. If omitted,
    // each variant's existing colorHex (if any) is left untouched; the swatch falls back to the
    // color-name-derived shade.
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "newColorHex must be a 6-digit hex code, e.g. #A9C6E8")
    private String newColorHex;
}
