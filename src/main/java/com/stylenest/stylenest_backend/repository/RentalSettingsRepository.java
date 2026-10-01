package com.stylenest.stylenest_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.RentalSettings;

public interface RentalSettingsRepository extends JpaRepository<RentalSettings, Long> {
}
