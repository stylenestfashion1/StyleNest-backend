package com.stylenest.stylenest_backend.dto.discount;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountRangeResponse {

    private Integer minDiscountPercentage;
    private Integer maxDiscountPercentage;
}
