package com.stylenest.stylenest_backend.dto.discount;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * The 1-100 bounds here are only the ABSOLUTE floor/ceiling (matching
 * DiscountConfigServiceImpl.ABSOLUTE_FLOOR/ABSOLUTE_CEILING) -- the real,
 * business-configured min/max (e.g. 15-25%) is admin-editable via
 * DiscountRangeConfig and checked dynamically in
 * DiscountConfigServiceImpl.updateConfig, not hard-coded here.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountSlotRequest {

    @NotNull(message = "discountPercentage is required")
    @Min(value = 1, message = "discountPercentage must be between 1 and 100")
    @Max(value = 100, message = "discountPercentage must be between 1 and 100")
    private Integer discountPercentage;

    @NotNull(message = "probabilityPercentage is required")
    @Min(value = 0, message = "probabilityPercentage must be between 0 and 100")
    @Max(value = 100, message = "probabilityPercentage must be between 0 and 100")
    private Integer probabilityPercentage;
}
