package com.stylenest.stylenest_backend.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.stylenest.stylenest_backend.enums.Gender;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;

    private String name;

    private String slug;

    @Schema(description = "Short, stable product code (e.g. \"UGT\") used as the prefix for "
            + "every variant SKU (e.g. \"STN-UGT-BLK-M\"). Auto-generated once at creation.")
    private String sku;

    private String shortDescription;

    private String description;

    @Schema(description = "The INR price -- kept for backward compatibility with existing "
            + "consumers. Always equal to the INR entry in prices[] below.")
    private BigDecimal price;

    @Schema(description = "The INR sale price -- kept for backward compatibility with existing "
            + "consumers. Always equal to the INR entry in prices[] below.")
    private BigDecimal discountPrice;

    @Schema(description = "Every currency this product currently has independent pricing in. "
            + "INR is always present. USD is present only if the admin has configured it -- "
            + "its absence means international pricing is not yet available for this product, "
            + "not that it should fall back to the INR price.")
    @Builder.Default
    private List<ProductPriceResponse> prices = new java.util.ArrayList<>();

    private String fabric;

    private String careInstructions;

    private String hsnCode;

    private Boolean featured;

    private Boolean trending;

    private Boolean active;

    @Schema(description = "URL of the first image (lowest displayOrder) belonging to the "
            + "product's first variant that has images. Null if the product has no "
            + "variant images yet.")
    private String thumbnailUrl;

    @Schema(description = "Distinct colors across this product's variants. Empty (not null) "
            + "if the product has no variants.")
    private List<String> availableColors;

    private String categoryName;

    @Schema(description = "The customer segment this product belongs to, inherited from its category.")
    private Gender gender;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}