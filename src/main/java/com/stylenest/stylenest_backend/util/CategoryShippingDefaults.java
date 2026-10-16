package com.stylenest.stylenest_backend.util;

import java.math.BigDecimal;

/**
 * Provides industry-standard apparel shipping weights (grams) and package dimensions (cm)
 * keyed by garment category. These smart defaults guarantee DTDC shipping cost accuracy
 * and ensure external catalog feeds (Shopdeck, Meta, Google Shopping) receive complete,
 * valid physical specifications when an admin does not manually override them.
 */
public final class CategoryShippingDefaults {

    public record ShippingPreset(
            BigDecimal weightGrams,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            String defaultHsnCode
    ) {}

    // Heavy denim / jeans
    public static final ShippingPreset JEANS = new ShippingPreset(
            BigDecimal.valueOf(650), BigDecimal.valueOf(35), BigDecimal.valueOf(28), BigDecimal.valueOf(6), "62046200");

    // 2-piece / 3-piece suit sets, long gowns
    public static final ShippingPreset SUIT_GOWN = new ShippingPreset(
            BigDecimal.valueOf(550), BigDecimal.valueOf(35), BigDecimal.valueOf(26), BigDecimal.valueOf(5), "62041911");

    // 2-piece Co-ord sets (Men/Women top + bottom)
    public static final ShippingPreset CORDSET = new ShippingPreset(
            BigDecimal.valueOf(500), BigDecimal.valueOf(32), BigDecimal.valueOf(25), BigDecimal.valueOf(5), "61034900");

    // Lower, Trackpants, Joggers, Palazzo
    public static final ShippingPreset BOTTOMS = new ShippingPreset(
            BigDecimal.valueOf(400), BigDecimal.valueOf(32), BigDecimal.valueOf(24), BigDecimal.valueOf(4), "62046900");

    // Kurti, One Piece, Dresses
    public static final ShippingPreset KURTI_DRESS = new ShippingPreset(
            BigDecimal.valueOf(350), BigDecimal.valueOf(30), BigDecimal.valueOf(22), BigDecimal.valueOf(3), "62044220");

    // T-shirts, Polo, Shirts, Tops (lightweight knitted/woven tops)
    public static final ShippingPreset TSHIRT_TOPS = new ShippingPreset(
            BigDecimal.valueOf(300), BigDecimal.valueOf(30), BigDecimal.valueOf(20), BigDecimal.valueOf(3), "61091000");

    // General fallback for unspecified apparel
    public static final ShippingPreset GENERAL_DEFAULT = new ShippingPreset(
            BigDecimal.valueOf(350), BigDecimal.valueOf(30), BigDecimal.valueOf(20), BigDecimal.valueOf(4), "61091000");

    private CategoryShippingDefaults() {}

    public static ShippingPreset forCategory(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return GENERAL_DEFAULT;
        }
        String norm = categoryName.trim().toLowerCase();
        if (norm.contains("jean") || norm.contains("denim")) {
            return JEANS;
        }
        if (norm.contains("suit") || norm.contains("gown")) {
            return SUIT_GOWN;
        }
        if (norm.contains("cordset") || norm.contains("coord") || norm.contains("co-ord")) {
            return CORDSET;
        }
        if (norm.contains("lower") || norm.contains("palazzo") || norm.contains("track") || norm.contains("pant") || norm.contains("jogger")) {
            return BOTTOMS;
        }
        if (norm.contains("kurti") || norm.contains("one piece") || norm.contains("dress")) {
            return KURTI_DRESS;
        }
        if (norm.contains("t-shirt") || norm.contains("tshirt") || norm.contains("top") || norm.contains("shirt") || norm.contains("polo")) {
            return TSHIRT_TOPS;
        }
        return GENERAL_DEFAULT;
    }
}
