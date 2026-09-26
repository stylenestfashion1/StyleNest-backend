package com.stylenest.stylenest_backend.dto.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

// International (non-INR) pricing for one product, submitted only when the
// admin has explicitly checked "Enable international pricing" -- see
// ProductRequest.internationalPrice. Omitting this object entirely on an
// update leaves any existing international pricing untouched; it is never
// wiped by omission.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPriceRequest {

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal regularPrice;

    @DecimalMin("0.0")
    private BigDecimal discountPrice;
}
