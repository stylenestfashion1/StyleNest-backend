package com.stylenest.stylenest_backend.dto.discount;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountSlotResponse {

    private Long id;
    private Integer discountPercentage;
    private Integer probabilityPercentage;
}
