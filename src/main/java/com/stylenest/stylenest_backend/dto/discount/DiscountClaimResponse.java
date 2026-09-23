package com.stylenest.stylenest_backend.dto.discount;

import java.time.LocalDateTime;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountClaimResponse {

    private String customerName;
    private Integer discountPercentage;
    private String status;
    private LocalDateTime generatedAt;
}
