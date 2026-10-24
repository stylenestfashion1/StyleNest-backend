package com.stylenest.stylenest_backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class NumberToWordsUtilTest {

    @Test
    void toIndianRupeeWords_convertsCorrectly() {
        assertThat(NumberToWordsUtil.toIndianRupeeWords(new BigDecimal("1699.00")))
                .isEqualTo("One Thousand Six Hundred Ninety Nine Rupees only");

        assertThat(NumberToWordsUtil.toIndianRupeeWords(new BigDecimal("25450.75")))
                .isEqualTo("Twenty Five Thousand Four Hundred Fifty Rupees and Seventy Five Paise only");
    }

    @Test
    void toUsdWords_convertsCorrectly() {
        assertThat(NumberToWordsUtil.toUsdWords(new BigDecimal("350.00")))
                .isEqualTo("Three Hundred Fifty US Dollars only");

        assertThat(NumberToWordsUtil.toUsdWords(new BigDecimal("178.80")))
                .isEqualTo("One Hundred Seventy Eight US Dollars and Eighty Cents only");

        assertThat(NumberToWordsUtil.toUsdWords(new BigDecimal("1.00")))
                .isEqualTo("One US Dollar only");
    }
}
