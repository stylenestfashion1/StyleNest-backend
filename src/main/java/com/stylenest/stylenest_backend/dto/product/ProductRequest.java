package com.stylenest.stylenest_backend.dto.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank
    private String name;

    private String shortDescription;

    private String description;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal price;

    private BigDecimal discountPrice;

    private String fabric;

    private String careInstructions;

    @Builder.Default
    private Boolean featured = false;

    @Builder.Default
    private Boolean trending = false;

    @Builder.Default
    private Boolean active = true;

    @NotNull
    private Long categoryId;
}