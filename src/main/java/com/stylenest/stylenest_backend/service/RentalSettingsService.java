package com.stylenest.stylenest_backend.service;

import org.springframework.web.multipart.MultipartFile;

import com.stylenest.stylenest_backend.dto.rental.RentalPublicSettingsResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalSettingsResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalSettingsUpdateRequest;
import com.stylenest.stylenest_backend.entity.RentalSettings;

public interface RentalSettingsService {

    /** The live settings entity -- for other rental services to read internally (e.g. season dates, expiry minutes). Creates the default singleton row on first call if none exists yet. */
    RentalSettings currentSettings();

    // -- Admin --

    RentalSettingsResponse getSettings();

    RentalSettingsResponse updateSettings(RentalSettingsUpdateRequest request);

    RentalSettingsResponse uploadPaymentQr(MultipartFile file);

    // -- Public (share-token scoped, same trust boundary as the catalog itself) --

    RentalPublicSettingsResponse getPublicSettings(String shareToken);
}
