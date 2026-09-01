package com.stylenest.stylenest_backend.dto.product;

import com.stylenest.stylenest_backend.enums.Color;
import com.stylenest.stylenest_backend.enums.Size;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantRequest {

    @NotNull
    private Color color;

    @NotNull
    private Size size;

    @NotNull
    @Min(0)
    private Integer stock;
}