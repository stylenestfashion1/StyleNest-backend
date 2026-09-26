package com.stylenest.stylenest_backend.dto.product;

import java.math.BigDecimal;

import com.stylenest.stylenest_backend.enums.Currency;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPriceResponse {

    private Currency currency;

    private BigDecimal regularPrice;

    @Schema(description = "Null when there is no active sale in this currency.")
    private BigDecimal discountPrice;
}
