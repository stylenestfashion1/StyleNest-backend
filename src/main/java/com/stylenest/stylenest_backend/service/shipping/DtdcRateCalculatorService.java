package com.stylenest.stylenest_backend.service.shipping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.stylenest.stylenest_backend.dto.shipping.ShippingCalculationResponse;
import com.stylenest.stylenest_backend.entity.ProductVariant;

/**
 * Authoritative DTDC Rate Calculator implementing the contracted commercials
 * for StyleNest Fashion Private Limited (DTDC Agreement Annexure II: Fees).
 *
 * Origin: Indore, Madhya Pradesh (Pincode: 452010).
 * Service: Product 7D (Ground Express / Surface - standard for retail apparel).
 * Volumetric Factor: (L x W x H in cm) / 4750 (Surface B2C Ground).
 * Chargeable Weight: Higher of actual weight or volumetric weight.
 * Default baseline fallback weight: 500 grams per apparel piece.
 * All rates exclude GST (18% GST charged additionally).
 */
@Service
public class DtdcRateCalculatorService {

    public static final BigDecimal DEFAULT_ITEM_WEIGHT_GRAMS = BigDecimal.valueOf(500);
    private static final BigDecimal VOLUMETRIC_DIVISOR = BigDecimal.valueOf(4750);
    private static final BigDecimal GST_RATE = BigDecimal.valueOf(0.18);
    private static final BigDecimal WEIGHT_SLAB_GRAMS = BigDecimal.valueOf(500);

    public enum DtdcZone {
        LOCAL(BigDecimal.valueOf(36.00), BigDecimal.valueOf(25.00), "1-2 Business Days"),
        REGIONAL(BigDecimal.valueOf(39.00), BigDecimal.valueOf(28.00), "2-3 Business Days"),
        METRO(BigDecimal.valueOf(54.00), BigDecimal.valueOf(39.00), "3-5 Business Days"),
        ROI(BigDecimal.valueOf(57.00), BigDecimal.valueOf(40.00), "4-6 Business Days"),
        SPL_DEST(BigDecimal.valueOf(66.00), BigDecimal.valueOf(50.00), "5-7 Business Days");

        private final BigDecimal baseRate500g;
        private final BigDecimal additionalRate500g;
        private final String estimatedDelivery;

        DtdcZone(BigDecimal baseRate, BigDecimal additionalRate, String delivery) {
            this.baseRate500g = baseRate;
            this.additionalRate500g = additionalRate;
            this.estimatedDelivery = delivery;
        }

        public BigDecimal getBaseRate500g() {
            return baseRate500g;
        }

        public BigDecimal getAdditionalRate500g() {
            return additionalRate500g;
        }

        public String getEstimatedDelivery() {
            return estimatedDelivery;
        }
    }

    public static class PhysicalItemSpec {
        private final BigDecimal weightGrams;
        private final BigDecimal lengthCm;
        private final BigDecimal widthCm;
        private final BigDecimal heightCm;
        private final int quantity;

        public PhysicalItemSpec(BigDecimal weightGrams, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, int quantity) {
            this.weightGrams = weightGrams;
            this.lengthCm = lengthCm;
            this.widthCm = widthCm;
            this.heightCm = heightCm;
            this.quantity = Math.max(1, quantity);
        }

        public static PhysicalItemSpec fromVariant(ProductVariant variant, int quantity) {
            BigDecimal weight = variant.getShippingWeightGrams();
            if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
                if (variant.getProduct() != null && variant.getProduct().getShippingWeightGrams() != null) {
                    weight = variant.getProduct().getShippingWeightGrams();
                }
            }
            if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
                weight = DEFAULT_ITEM_WEIGHT_GRAMS;
            }

            BigDecimal length = variant.getPackageLengthCm() != null ? variant.getPackageLengthCm() :
                    (variant.getProduct() != null ? variant.getProduct().getPackageLengthCm() : null);
            BigDecimal width = variant.getPackageWidthCm() != null ? variant.getPackageWidthCm() :
                    (variant.getProduct() != null ? variant.getProduct().getPackageWidthCm() : null);
            BigDecimal height = variant.getPackageHeightCm() != null ? variant.getPackageHeightCm() :
                    (variant.getProduct() != null ? variant.getProduct().getPackageHeightCm() : null);

            return new PhysicalItemSpec(weight, length, width, height, quantity);
        }

        public static PhysicalItemSpec defaultApparel(int quantity) {
            return new PhysicalItemSpec(DEFAULT_ITEM_WEIGHT_GRAMS, null, null, null, quantity);
        }

        public BigDecimal computeChargeableWeightGrams() {
            BigDecimal actual = weightGrams != null && weightGrams.compareTo(BigDecimal.ZERO) > 0
                    ? weightGrams : DEFAULT_ITEM_WEIGHT_GRAMS;

            BigDecimal volumetric = BigDecimal.ZERO;
            if (lengthCm != null && widthCm != null && heightCm != null
                    && lengthCm.compareTo(BigDecimal.ZERO) > 0
                    && widthCm.compareTo(BigDecimal.ZERO) > 0
                    && heightCm.compareTo(BigDecimal.ZERO) > 0) {
                // Volumetric weight in KG = (L * W * H) / 4750 -> in grams = * 1000
                BigDecimal volKg = lengthCm.multiply(widthCm).multiply(heightCm)
                        .divide(VOLUMETRIC_DIVISOR, 4, RoundingMode.HALF_UP);
                volumetric = volKg.multiply(BigDecimal.valueOf(1000));
            }

            BigDecimal singlePiece = actual.max(volumetric);
            return singlePiece.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /**
     * Determines DTDC shipping zone based on destination pincode, city and state.
     */
    public DtdcZone resolveZone(String postalCode, String city, String state) {
        String pin = normalizePostalCode(postalCode);
        String normCity = (city == null ? "" : city.trim().toLowerCase());
        String normState = (state == null ? "" : state.trim().toLowerCase());

        // 1. Local (Indore and neighboring hubs: 452xxx, 453xxx)
        if (pin.startsWith("452") || pin.startsWith("453") || normCity.contains("indore")) {
            return DtdcZone.LOCAL;
        }

        // 2. Metro Destinations (per DTDC Agreement Page 16: Mumbai, Delhi, Ahmedabad, Bengaluru, Pune, Chennai, Kolkata, Hyderabad)
        if (isMetro(pin, normCity)) {
            return DtdcZone.METRO;
        }

        // 3. Regional (Madhya Pradesh State)
        if (normState.contains("madhya pradesh") || normState.equals("mp")
                || pin.startsWith("45") || pin.startsWith("46") || pin.startsWith("47") || pin.startsWith("48")) {
            return DtdcZone.REGIONAL;
        }

        // 4. Special Destinations (North East: 78xxx, 79xxx, J&K: 19xxx, 18xxx)
        if (pin.startsWith("78") || pin.startsWith("79") || pin.startsWith("19") || pin.startsWith("18")
                || normState.contains("assam") || normState.contains("meghalaya") || normState.contains("jammu")
                || normState.contains("kashmir") || normState.contains("manipur") || normState.contains("nagaland")) {
            return DtdcZone.SPL_DEST;
        }

        // 5. Rest of India
        return DtdcZone.ROI;
    }

    private boolean isMetro(String pin, String city) {
        if (pin.startsWith("110") || city.contains("delhi")) return true;
        if (pin.startsWith("400") || city.contains("mumbai") || city.contains("thane") || city.contains("navi mumbai")) return true;
        if (pin.startsWith("560") || city.contains("bangalore") || city.contains("bengaluru")) return true;
        if (pin.startsWith("700") || city.contains("kolkata") || city.contains("calcutta")) return true;
        if (pin.startsWith("600") || city.contains("chennai") || city.contains("madras")) return true;
        if (pin.startsWith("500") || city.contains("hyderabad") || city.contains("secunderabad")) return true;
        if (pin.startsWith("411") || city.contains("pune")) return true;
        if (pin.startsWith("380") || city.contains("ahmedabad")) return true;
        return false;
    }

    private String normalizePostalCode(String postalCode) {
        if (postalCode == null) return "";
        return postalCode.replaceAll("\\D", "");
    }

    /**
     * Calculates total shipping fee for an order.
     */
    public ShippingCalculationResponse calculateShipping(String postalCode, String city, String state, List<PhysicalItemSpec> items) {
        DtdcZone zone = resolveZone(postalCode, city, state);

        BigDecimal totalWeightGrams = BigDecimal.ZERO;
        if (items != null && !items.isEmpty()) {
            for (PhysicalItemSpec item : items) {
                totalWeightGrams = totalWeightGrams.add(item.computeChargeableWeightGrams());
            }
        } else {
            totalWeightGrams = DEFAULT_ITEM_WEIGHT_GRAMS;
        }

        BigDecimal baseCharge;
        if (totalWeightGrams.compareTo(WEIGHT_SLAB_GRAMS) <= 0) {
            baseCharge = zone.getBaseRate500g();
        } else {
            BigDecimal excessGrams = totalWeightGrams.subtract(WEIGHT_SLAB_GRAMS);
            int additionalSlabs = (int) Math.ceil(excessGrams.doubleValue() / WEIGHT_SLAB_GRAMS.doubleValue());
            BigDecimal additionalCharges = zone.getAdditionalRate500g().multiply(BigDecimal.valueOf(additionalSlabs));
            baseCharge = zone.getBaseRate500g().add(additionalCharges);
        }

        BigDecimal gstAmount = baseCharge.multiply(GST_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalShippingFee = baseCharge.add(gstAmount).setScale(0, RoundingMode.CEILING).setScale(2, RoundingMode.UNNECESSARY);

        return ShippingCalculationResponse.builder()
                .zone(zone.name())
                .serviceType("DTDC Ground Economy")
                .chargeableWeightGrams(totalWeightGrams.setScale(0, RoundingMode.HALF_UP))
                .baseCharge(baseCharge.setScale(2, RoundingMode.HALF_UP))
                .gstAmount(gstAmount)
                .totalShippingFee(totalShippingFee)
                .estimatedDelivery(zone.getEstimatedDelivery())
                .build();
    }
}
