package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.stylenest.stylenest_backend.config.GstRuleProperties;
import com.stylenest.stylenest_backend.service.GstCalculationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GstCalculationServiceImpl implements GstCalculationService {

    private final GstRuleProperties gstRules;

    @Override
    public BigDecimal resolveApparelGstRate(BigDecimal chargedUnitPriceInclusive) {

        return chargedUnitPriceInclusive.compareTo(gstRules.getApparelThreshold()) <= 0
                ? gstRules.getRateAtOrBelowThreshold()
                : gstRules.getRateAboveThreshold();
    }
}
