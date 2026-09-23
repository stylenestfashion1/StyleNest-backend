package com.stylenest.stylenest_backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Converts a rupee amount to Indian-numbering-system words, e.g. "One Hundred Sixty Nine Rupees only". */
public class NumberToWordsUtil {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private NumberToWordsUtil() {}

    public static String toIndianRupeeWords(BigDecimal amount) {

        if (amount == null) {
            return "";
        }

        long rupees = amount.setScale(0, RoundingMode.DOWN).longValueExact();
        int paise = amount.subtract(BigDecimal.valueOf(rupees))
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        StringBuilder sb = new StringBuilder();

        if (rupees == 0) {
            sb.append("Zero Rupees");
        } else {
            sb.append(convert(rupees)).append(" Rupees");
        }

        if (paise > 0) {
            sb.append(" and ").append(convert(paise)).append(" Paise");
        }

        sb.append(" only");

        return sb.toString();
    }

    private static String convert(long number) {

        if (number == 0) {
            return "";
        }
        if (number < 20) {
            return ONES[(int) number];
        }
        if (number < 100) {
            return (TENS[(int) (number / 10)] + " " + ONES[(int) (number % 10)]).trim();
        }
        if (number < 1000) {
            return (ONES[(int) (number / 100)] + " Hundred " + convert(number % 100)).trim();
        }
        if (number < 100_000) {
            return (convert(number / 1000) + " Thousand " + convert(number % 1000)).trim();
        }
        if (number < 10_000_000) {
            return (convert(number / 100_000) + " Lakh " + convert(number % 100_000)).trim();
        }
        return (convert(number / 10_000_000) + " Crore " + convert(number % 10_000_000)).trim();
    }
}
