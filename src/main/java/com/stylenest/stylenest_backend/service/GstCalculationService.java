package com.stylenest.stylenest_backend.service;

import java.math.BigDecimal;

/**
 * The single reusable GST-rate-resolution engine for the whole app. Every
 * flow that needs to know "what GST rate applies to this sale" -- invoice
 * generation (retail and bulk), and checkout/order calculation if it ever
 * needs one -- must go through this, never re-implement the slab rule
 * itself. See GstRuleProperties for the actual configured threshold/rates.
 */
public interface GstCalculationService {

    /**
     * Resolves the applicable GST rate (as a whole-number percentage, e.g.
     * 5 or 18) for one unit sold at the given tax-inclusive charged price.
     * Always driven by the ACTUAL sale price, never the MRP.
     */
    BigDecimal resolveApparelGstRate(BigDecimal chargedUnitPriceInclusive);
}
