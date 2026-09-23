package com.stylenest.stylenest_backend.dto.discount;

import java.util.List;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountConfigResponse {

    // The REAL discount configuration only -- deliberately does NOT include
    // the QR display range, which is a fully separate, independently
    // managed setting (see DiscountRangeConfig / DiscountConfigService#getDisplayRange).
    private List<DiscountSlotResponse> slots;
    private Integer totalProbability;
}
