package com.stylenest.stylenest_backend.service.impl;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.stylenest.stylenest_backend.dto.rental.RentalPublicSettingsResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalSettingsResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalSettingsUpdateRequest;
import com.stylenest.stylenest_backend.entity.RentalCatalog;
import com.stylenest.stylenest_backend.entity.RentalSettings;
import com.stylenest.stylenest_backend.enums.RentalCatalogStatus;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.RentalSettingsMapper;
import com.stylenest.stylenest_backend.repository.RentalCatalogRepository;
import com.stylenest.stylenest_backend.repository.RentalSettingsRepository;
import com.stylenest.stylenest_backend.service.RentalImageStorageService;
import com.stylenest.stylenest_backend.service.RentalSettingsService;

import lombok.RequiredArgsConstructor;

/**
 * Always exactly one row (id = RentalSettings.SINGLETON_ID), same
 * lazy-create-on-first-read pattern as
 * DiscountConfigServiceImpl#currentRange -- a fresh boot never needs a
 * dedicated seeder/runner, and an admin's saved customization is never
 * reset by a restart.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RentalSettingsServiceImpl implements RentalSettingsService {

    // Defaults on first boot -- see spec sections 1 and 22. Purely a
    // starting point; every field is admin-editable afterward via Admin ->
    // Rental (Navratri) -> Rental Settings.
    private static final LocalDate DEFAULT_SEASON_START = LocalDate.of(2026, 10, 11);
    private static final LocalDate DEFAULT_SEASON_END = LocalDate.of(2026, 10, 19);
    private static final String DEFAULT_WHATSAPP = "6269933231";
    private static final int DEFAULT_PENDING_EXPIRY_MINUTES = 30;
    private static final String DEFAULT_PICKUP =
            "Customer can collect the lehenga from the shop between 4:00 PM and 7:00 PM on the pickup date.";
    private static final String DEFAULT_RETURN =
            "Lehenga must be returned before 12:00 PM on the agreed return date.";
    private static final String DEFAULT_LATE_RETURN = "Additional charges may apply after the return deadline.";
    private static final String DEFAULT_PAYMENT_INSTRUCTIONS =
            "Customer must complete the required payment and send the payment screenshot and transaction ID to the shop WhatsApp number.";
    private static final String DEFAULT_TERMS = """
            IMPORTANT RENTAL TERMS & CONDITIONS

            1. Lehenga pickup time: Customer can collect the lehenga from the shop between 4:00 PM and 7:00 PM on the pickup date.
            2. Return time: Lehenga must be returned before 12:00 PM on the agreed return date.
            3. Late return: Additional charges may apply after the return deadline.
            4. Availability: Booking is subject to payment verification and shop confirmation.
            5. Payment: Customer must complete the required payment and send the payment screenshot and transaction ID to the shop WhatsApp number.
            """;

    private final RentalSettingsRepository rentalSettingsRepository;
    private final RentalCatalogRepository rentalCatalogRepository;
    private final RentalSettingsMapper rentalSettingsMapper;
    private final RentalImageStorageService rentalImageStorageService;

    @Override
    @Transactional(readOnly = true)
    public RentalSettings currentSettings() {
        return loadOrSeed();
    }

    @Override
    @Transactional(readOnly = true)
    public RentalSettingsResponse getSettings() {
        return rentalSettingsMapper.toResponse(loadOrSeed());
    }

    @Override
    public RentalSettingsResponse updateSettings(RentalSettingsUpdateRequest request) {

        RentalSettings settings = loadOrSeed();

        settings.setSeasonStartDate(request.getSeasonStartDate());
        settings.setSeasonEndDate(request.getSeasonEndDate());
        settings.setWhatsappNumber(request.getWhatsappNumber().trim());
        settings.setPickupInstructions(request.getPickupInstructions());
        settings.setReturnInstructions(request.getReturnInstructions());
        settings.setLateReturnMessage(request.getLateReturnMessage());
        settings.setPaymentInstructions(request.getPaymentInstructions());
        settings.setTermsAndConditions(request.getTermsAndConditions());
        settings.setPendingPaymentExpiryMinutes(request.getPendingPaymentExpiryMinutes());

        return rentalSettingsMapper.toResponse(rentalSettingsRepository.save(settings));
    }

    @Override
    public RentalSettingsResponse uploadPaymentQr(MultipartFile file) {

        RentalSettings settings = loadOrSeed();

        String previousUrl = settings.getPaymentQrImageUrl();

        settings.setPaymentQrImageUrl(rentalImageStorageService.store(file).getUrl());

        RentalSettings saved = rentalSettingsRepository.save(settings);

        if (previousUrl != null) {
            rentalImageStorageService.deleteIfManaged(previousUrl);
        }

        return rentalSettingsMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RentalPublicSettingsResponse getPublicSettings(String shareToken) {

        RentalCatalog catalog = rentalCatalogRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Rental catalog not found."));

        if (catalog.getStatus() != RentalCatalogStatus.ACTIVE) {
            throw new ResourceNotFoundException("Rental catalog not found.");
        }

        return rentalSettingsMapper.toPublicResponse(loadOrSeed());
    }

    private RentalSettings loadOrSeed() {

        return rentalSettingsRepository.findById(RentalSettings.SINGLETON_ID)
                .orElseGet(() -> rentalSettingsRepository.save(RentalSettings.builder()
                        .id(RentalSettings.SINGLETON_ID)
                        .seasonStartDate(DEFAULT_SEASON_START)
                        .seasonEndDate(DEFAULT_SEASON_END)
                        .whatsappNumber(DEFAULT_WHATSAPP)
                        .pickupInstructions(DEFAULT_PICKUP)
                        .returnInstructions(DEFAULT_RETURN)
                        .lateReturnMessage(DEFAULT_LATE_RETURN)
                        .paymentInstructions(DEFAULT_PAYMENT_INSTRUCTIONS)
                        .termsAndConditions(DEFAULT_TERMS)
                        .pendingPaymentExpiryMinutes(DEFAULT_PENDING_EXPIRY_MINUTES)
                        .build()));
    }
}
