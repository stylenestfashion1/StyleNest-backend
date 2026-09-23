package com.stylenest.stylenest_backend.dto.discount;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Bean-validation here enforces the ABSOLUTE, non-negotiable floor/ceiling
 * (1-100) -- a typo can never produce a nonsensical or unsafe value. The
 * service layer additionally enforces min < max; see
 * DiscountConfigServiceImpl.updateRange.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountRangeUpdateRequest {

    @NotNull(message = "minDiscountPercentage is required")
    @Min(value = 1, message = "minDiscountPercentage must be between 1 and 100")
    @Max(value = 100, message = "minDiscountPercentage must be between 1 and 100")
    private Integer minDiscountPercentage;

    @NotNull(message = "maxDiscountPercentage is required")
    @Min(value = 1, message = "maxDiscountPercentage must be between 1 and 100")
    @Max(value = 100, message = "maxDiscountPercentage must be between 1 and 100")
    private Integer maxDiscountPercentage;
}
