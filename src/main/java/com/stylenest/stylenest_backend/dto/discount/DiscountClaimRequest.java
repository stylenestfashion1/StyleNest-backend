package com.stylenest.stylenest_backend.dto.discount;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Deliberately carries ONLY the two fields a customer actually enters.
 * discountPercentage and status are never accepted from the client -- the
 * backend is the sole authority for both (see DiscountOfferServiceImpl).
 *
 * Mobile number format is validated in the service layer, not here via
 * @Pattern -- it must first be normalized (strip spaces/dashes/+91) before
 * the "exactly 10 digits" check runs, which bean validation on the raw
 * field can't do.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountClaimRequest {

    @NotBlank(message = "Name is required")
    private String customerName;

    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;
}
