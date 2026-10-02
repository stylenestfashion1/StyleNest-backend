package com.stylenest.stylenest_backend.service.shipping;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stylenest.stylenest_backend.dto.shipping.ShippingCalculationResponse;

import static org.assertj.core.api.Assertions.assertThat;

class DtdcRateCalculatorServiceTest {

    private DtdcRateCalculatorService calculator;

    @BeforeEach
    void setUp() {
        calculator = new DtdcRateCalculatorService();
    }

    @Test
    @DisplayName("Local Indore (< 500g): Base 36.00 + 18% GST = 42.48")
    void testLocalIndoreUnder500g() {
        var items = List.of(DtdcRateCalculatorService.PhysicalItemSpec.defaultApparel(1)); // 350g
        ShippingCalculationResponse res = calculator.calculateShipping("452010", "Indore", "Madhya Pradesh", items);

        assertThat(res.getZone()).isEqualTo("LOCAL");
        assertThat(res.getBaseCharge()).isEqualByComparingTo("36.00");
        assertThat(res.getGstAmount()).isEqualByComparingTo("6.48");
        assertThat(res.getTotalShippingFee()).isEqualByComparingTo("42.48");
    }

    @Test
    @DisplayName("Metro Bangalore (< 500g): Base 54.00 + 18% GST = 63.72")
    void testMetroBangaloreSingleItem() {
        var items = List.of(DtdcRateCalculatorService.PhysicalItemSpec.defaultApparel(1)); // 350g
        ShippingCalculationResponse res = calculator.calculateShipping("560001", "Bengaluru", "Karnataka", items);

        assertThat(res.getZone()).isEqualTo("METRO");
        assertThat(res.getBaseCharge()).isEqualByComparingTo("54.00");
        assertThat(res.getGstAmount()).isEqualByComparingTo("9.72");
        assertThat(res.getTotalShippingFee()).isEqualByComparingTo("63.72");
    }

    @Test
    @DisplayName("Metro Bangalore (4 items = 1400g): Base 54 + (2 * 39) = 132.00 + 18% GST = 155.76")
    void testMetroBangaloreFourItems() {
        var items = List.of(DtdcRateCalculatorService.PhysicalItemSpec.defaultApparel(4)); // 4 * 350g = 1400g
        ShippingCalculationResponse res = calculator.calculateShipping("560001", "Bengaluru", "Karnataka", items);

        assertThat(res.getZone()).isEqualTo("METRO");
        assertThat(res.getChargeableWeightGrams()).isEqualByComparingTo("1400");
        assertThat(res.getBaseCharge()).isEqualByComparingTo("132.00");
        assertThat(res.getGstAmount()).isEqualByComparingTo("23.76");
        assertThat(res.getTotalShippingFee()).isEqualByComparingTo("155.76");
    }

    @Test
    @DisplayName("Regional MP (Bhopal < 500g): Base 39.00 + 18% GST = 46.02")
    void testRegionalMpSingleItem() {
        var items = List.of(DtdcRateCalculatorService.PhysicalItemSpec.defaultApparel(1));
        ShippingCalculationResponse res = calculator.calculateShipping("462001", "Bhopal", "Madhya Pradesh", items);

        assertThat(res.getZone()).isEqualTo("REGIONAL");
        assertThat(res.getBaseCharge()).isEqualByComparingTo("39.00");
        assertThat(res.getGstAmount()).isEqualByComparingTo("7.02");
        assertThat(res.getTotalShippingFee()).isEqualByComparingTo("46.02");
    }

    @Test
    @DisplayName("Volumetric weight used when higher than actual weight")
    void testVolumetricWeightOverride() {
        // Actual 300g, but box is 30 x 20 x 10 = 6000 cm3 / 4750 = 1.263 kg = 1263g
        var item = new DtdcRateCalculatorService.PhysicalItemSpec(
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(10),
                1
        );
        ShippingCalculationResponse res = calculator.calculateShipping("452010", "Indore", "Madhya Pradesh", List.of(item));

        assertThat(res.getChargeableWeightGrams()).isEqualByComparingTo("1263");
        // 1263g -> base 500g + 2 additional slabs of 500g (up to 1500g)
        // Local: 36 + (2 * 25) = 86.00 + 18% GST (15.48) = 101.48
        assertThat(res.getBaseCharge()).isEqualByComparingTo("86.00");
        assertThat(res.getTotalShippingFee()).isEqualByComparingTo("101.48");
    }
}
