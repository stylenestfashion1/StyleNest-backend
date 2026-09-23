package com.stylenest.stylenest_backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.service.DiscountConfigService;

import lombok.RequiredArgsConstructor;

/**
 * Guarantees the 4 default QR discount slots exist on first boot. Idempotent
 * -- if slots already exist (the normal case after the first startup, and
 * after any admin customization), this is a no-op, so a restart never
 * resets an admin's configured percentages back to the defaults.
 */
@Component
@RequiredArgsConstructor
public class DiscountSlotSeeder implements ApplicationRunner {

    private final DiscountConfigService discountConfigService;

    @Override
    public void run(ApplicationArguments args) {
        discountConfigService.ensureDefaultSeeded();
    }
}
