package com.stylenest.stylenest_backend.dto.shipping;

import java.math.BigDecimal;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingCalculationResponse {

    private String zone;

    private String serviceType;

    private BigDecimal chargeableWeightGrams;

    private BigDecimal baseCharge;

    private BigDecimal gstAmount;

    private BigDecimal totalShippingFee;

    private String estimatedDelivery;
}
