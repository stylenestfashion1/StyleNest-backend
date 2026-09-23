package com.stylenest.stylenest_backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.service.BulkTokenService;

import lombok.RequiredArgsConstructor;

/**
 * Guarantees the 10 permanent bulk-order access tokens exist on every
 * startup. Idempotent -- if all 10 already exist (the normal case after the
 * first boot), this is a no-op; it only ever tops up a short count, never
 * removes or regenerates existing slots.
 */
@Component
@RequiredArgsConstructor
public class BulkTokenProvisioningRunner implements ApplicationRunner {

    private final BulkTokenService bulkTokenService;

    @Override
    public void run(ApplicationArguments args) {
        bulkTokenService.ensureTenPermanentTokensProvisioned();
    }
}
