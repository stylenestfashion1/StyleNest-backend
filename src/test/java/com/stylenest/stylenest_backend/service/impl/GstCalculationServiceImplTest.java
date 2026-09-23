package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.stylenest.stylenest_backend.config.GstRuleProperties;

/**
 * The centralized GST rate engine, independent of invoice generation.
 * Every threshold/rate here comes from GstRuleProperties -- never a literal
 * baked into this class -- so a future GST Council rate change only ever
 * requires editing that config, never this code (see the "future rule
 * change" test below, which proves exactly that).
 */
class GstCalculationServiceImplTest {

    @Test
    void belowThreshold_resolvesLowerRate() {

        GstCalculationServiceImpl service = new GstCalculationServiceImpl(new GstRuleProperties());

        assertThat(service.resolveApparelGstRate(new BigDecimal("1000"))).isEqualByComparingTo("5");
    }

    @Test
    void exactlyAtThreshold_resolvesLowerRate() {

        GstCalculationServiceImpl service = new GstCalculationServiceImpl(new GstRuleProperties());

        assertThat(service.resolveApparelGstRate(new BigDecimal("2500.00"))).isEqualByComparingTo("5");
    }

    @Test
    void aboveThreshold_resolvesHigherRate() {

        GstCalculationServiceImpl service = new GstCalculationServiceImpl(new GstRuleProperties());

        assertThat(service.resolveApparelGstRate(new BigDecimal("2500.01"))).isEqualByComparingTo("18");
    }

    @Test
    void futureRuleChange_onlyRequiresEditingGstRuleProperties_neverThisClass() {

        // Simulates the GST Council revising the rule in the future (e.g.
        // threshold raised to Rs.3000, lower rate becomes 12%) -- proves
        // the service picks up the new rule purely from config, with zero
        // code change to GstCalculationServiceImpl itself.
        GstRuleProperties futureRules = new GstRuleProperties();
        futureRules.setApparelThreshold(new BigDecimal("3000.00"));
        futureRules.setRateAtOrBelowThreshold(new BigDecimal("12"));
        futureRules.setRateAboveThreshold(new BigDecimal("20"));

        GstCalculationServiceImpl service = new GstCalculationServiceImpl(futureRules);

        assertThat(service.resolveApparelGstRate(new BigDecimal("2900"))).isEqualByComparingTo("12");
        assertThat(service.resolveApparelGstRate(new BigDecimal("3100"))).isEqualByComparingTo("20");
    }
}
