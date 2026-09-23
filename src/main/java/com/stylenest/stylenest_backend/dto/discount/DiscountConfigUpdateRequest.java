package com.stylenest.stylenest_backend.dto.discount;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountConfigUpdateRequest {

    @NotEmpty(message = "slots are required")
    @Valid
    private List<DiscountSlotRequest> slots;
}
