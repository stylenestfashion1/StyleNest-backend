package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.discount.DiscountConfigResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountConfigUpdateRequest;
import com.stylenest.stylenest_backend.dto.discount.DiscountRangeResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountRangeUpdateRequest;

public interface DiscountConfigService {

    /** Idempotent -- only seeds default slots + the default QR display range if none already exist. Called once at startup. */
    void ensureDefaultSeeded();

    /** The REAL discount configuration (slots + probabilities) that drives weighted-random selection. Never touches the QR display range. */
    DiscountConfigResponse getConfig();

    /** Replaces the REAL discount slots. Never reads or writes the QR display range -- the two are fully independent. */
    DiscountConfigResponse updateConfig(DiscountConfigUpdateRequest request);

    /** The QR poster's display-only min/max text (DiscountRangeConfig) -- purely cosmetic, never read by discount generation. */
    DiscountRangeResponse getDisplayRange();

    /** Updates the QR poster's display-only min/max text. Never reads or writes discount slots -- the two are fully independent. */
    DiscountRangeResponse updateRange(DiscountRangeUpdateRequest request);
}
