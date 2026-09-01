package com.stylenest.stylenest_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.OtpVerification;

public interface OtpVerificationRepository
        extends JpaRepository<OtpVerification, Long> {

	Optional<OtpVerification> findTopByEmailOrderByCreatedAtDesc(String email);
}