package com.stylenest.stylenest_backend.mapper;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.rental.RentalPublicSettingsResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalSettingsResponse;
import com.stylenest.stylenest_backend.entity.RentalSettings;

@Component
public class RentalSettingsMapper {

    public RentalSettingsResponse toResponse(RentalSettings settings) {

        return RentalSettingsResponse.builder()
                .seasonStartDate(settings.getSeasonStartDate())
                .seasonEndDate(settings.getSeasonEndDate())
                .whatsappNumber(settings.getWhatsappNumber())
                .paymentQrImageUrl(settings.getPaymentQrImageUrl())
                .pickupInstructions(settings.getPickupInstructions())
                .returnInstructions(settings.getReturnInstructions())
                .lateReturnMessage(settings.getLateReturnMessage())
                .paymentInstructions(settings.getPaymentInstructions())
                .termsAndConditions(settings.getTermsAndConditions())
                .pendingPaymentExpiryMinutes(settings.getPendingPaymentExpiryMinutes())
                .build();
    }

    public RentalPublicSettingsResponse toPublicResponse(RentalSettings settings) {

        return RentalPublicSettingsResponse.builder()
                .seasonStartDate(settings.getSeasonStartDate())
                .seasonEndDate(settings.getSeasonEndDate())
                .whatsappNumber(settings.getWhatsappNumber())
                .paymentQrImageUrl(settings.getPaymentQrImageUrl())
                .pickupInstructions(settings.getPickupInstructions())
                .returnInstructions(settings.getReturnInstructions())
                .lateReturnMessage(settings.getLateReturnMessage())
                .paymentInstructions(settings.getPaymentInstructions())
                .termsAndConditions(settings.getTermsAndConditions())
                .build();
    }
}
