package com.stylenest.stylenest_backend.dto.product;

import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank
    private String name;

    // Optional public-URL override -- omitted/blank means "auto-generate
    // from name" on create, or "leave exactly as it currently is" on
    // update (the slug is never silently regenerated just because the
    // name changed, so existing shared/bookmarked/indexed product URLs
    // keep working). When provided, it's normalized the same way an
    // auto-generated slug is and rejected if it collides with another
    // product's slug. See ProductServiceImpl.resolveSlugForCreate/Update.
    private String slug;

    private String shortDescription;

    private String description;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal price;

    private BigDecimal discountPrice;

    // International (USD) pricing -- completely independent of price/
    // discountPrice above, never derived from them. Omitted entirely ->
    // any existing international pricing is left exactly as it was (never
    // wiped by omission). Present -> upserted as this product's USD price.
    // See ProductServiceImpl.syncInternationalPrice.
    @Valid
    private ProductPriceRequest internationalPrice;

    // Explicit "clear international pricing" signal, distinct from simply
    // omitting internationalPrice above -- the admin form sends this only
    // when the "Enable international pricing" checkbox was unchecked on a
    // product that previously had a USD price, so removal is always a
    // deliberate action, never an accidental side effect of leaving a field
    // out of the request body.
    @Builder.Default
    private Boolean clearInternationalPricing = false;

    private String fabric;

    private String careInstructions;

    // Optional -- shown on invoices (see InvoiceGenerationServiceImpl) but
    // not required to create a product, since this catalog was never
    // seeded with real HSN data.
    private String hsnCode;

    // Admin-only internal identification code (see Product.jeansCode).
    // Optional -- may be null/blank. Letters, digits, spaces, hyphens and
    // underscores only; trimmed (not otherwise altered) before saving --
    // see ProductServiceImpl. Writing this field is already safe from a
    // public-facing standpoint because every endpoint that accepts a
    // ProductRequest body (POST/PUT on both /api/products/** and
    // /api/admin/products/**) already requires ROLE_ADMIN -- see
    // SecurityConfig. It is READING this value back that must stay
    // admin-only, which is why it is never added to ProductResponse.
    @Size(max = 64, message = "Jeans code must be at most 64 characters")
    @Pattern(regexp = "^[A-Za-z0-9 _-]*$", message = "Jeans code may only contain letters, numbers, spaces, hyphens and underscores")
    private String jeansCode;

    // Deliberately NO gstRate field here -- GST is never manually entered.
    // It is always computed automatically at invoice time from the actual
    // sale price, per the CBIC apparel threshold rule. See
    // InvoiceGenerationServiceImpl.resolveApparelGstRate.

    @Builder.Default
    private Boolean featured = false;

    @Builder.Default
    private Boolean trending = false;

    @Builder.Default
    private Boolean active = true;

    @NotNull
    private Long categoryId;
}