package com.stylenest.stylenest_backend.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.constant.OtpConstants;
import com.stylenest.stylenest_backend.entity.OtpVerification;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.OtpPurpose;
import com.stylenest.stylenest_backend.exception.InvalidOtpException;
import com.stylenest.stylenest_backend.exception.OtpAlreadyVerifiedException;
import com.stylenest.stylenest_backend.exception.OtpCooldownException;
import com.stylenest.stylenest_backend.exception.OtpExpiredException;
import com.stylenest.stylenest_backend.exception.TooManyOtpRequestsException;
import com.stylenest.stylenest_backend.exception.UserNotFoundException;
import com.stylenest.stylenest_backend.repository.OtpVerificationRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.OtpService;
import com.stylenest.stylenest_backend.util.OtpGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    private final OtpVerificationRepository otpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void sendOtp(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));

        LocalDateTime now = LocalDateTime.now(APP_ZONE);

        String otp = OtpGenerator.generateOtp();
        String hashedOtp = passwordEncoder.encode(otp);

        Optional<OtpVerification> existingOtp =
                otpRepository.findTopByEmailOrderByCreatedAtDesc(email);

        if (existingOtp.isPresent()) {

            OtpVerification otpVerification = existingOtp.get();

            if (otpVerification.getFirstRequestTime()
                    .plusHours(OtpConstants.OTP_REQUEST_WINDOW_HOURS)
                    .isBefore(now)) {

                otpVerification.setRequestCount(1);
                otpVerification.setFirstRequestTime(now);

            } else {

                if (otpVerification.getRequestCount()
                        >= OtpConstants.MAX_DAILY_OTP_REQUESTS) {

                    throw new TooManyOtpRequestsException(
                            "Maximum OTP request limit reached. Please try again after 24 hours.");
                }

                if (otpVerification.getCreatedAt()
                        .plusSeconds(OtpConstants.OTP_COOLDOWN_SECONDS)
                        .isAfter(now)) {

                    long remainingSeconds = Duration.between(
                            now,
                            otpVerification.getCreatedAt()
                                    .plusSeconds(OtpConstants.OTP_COOLDOWN_SECONDS))
                            .getSeconds();

                    throw new OtpCooldownException(
                            "Please wait "
                                    + getWaitMessage(remainingSeconds)
                                    + " before requesting another OTP.");
                }

                otpVerification.setRequestCount(
                        otpVerification.getRequestCount() + 1);
            }

            otpVerification.setOtp(hashedOtp);
            otpVerification.setExpiryTime(
                    now.plusMinutes(OtpConstants.OTP_EXPIRY_MINUTES));
            otpVerification.setCreatedAt(now);
            otpVerification.setAttempts(0);
            otpVerification.setVerified(false);
            otpVerification.setPurpose(OtpPurpose.FORGOT_PASSWORD);

            otpRepository.save(otpVerification);

        } else {

            OtpVerification otpVerification = OtpVerification.builder()
                    .email(user.getEmail())
                    .otp(hashedOtp)
                    .expiryTime(now.plusMinutes(OtpConstants.OTP_EXPIRY_MINUTES))
                    .createdAt(now)
                    .firstRequestTime(now)
                    .requestCount(1)
                    .attempts(0)
                    .verified(false)
                    .purpose(OtpPurpose.FORGOT_PASSWORD)
                    .build();

            otpRepository.save(otpVerification);
        }

        emailService.sendOtpEmail(email, otp);
    }    @Override
    @Transactional(
            noRollbackFor = {
                    InvalidOtpException.class,
                    OtpExpiredException.class,
                    OtpAlreadyVerifiedException.class
            }
    )
    public void verifyOtp(String email, String enteredOtp) {

        OtpVerification otpVerification = otpRepository
                .findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() ->
                        new InvalidOtpException(
                                "No active OTP found. Please request a new OTP."));

        if (otpVerification.getPurpose() != OtpPurpose.FORGOT_PASSWORD) {
            throw new InvalidOtpException("Invalid OTP purpose");
        }

        if (otpVerification.isVerified()) {
            throw new OtpAlreadyVerifiedException("OTP already verified");
        }

        if (otpVerification.getExpiryTime()
                .isBefore(LocalDateTime.now(APP_ZONE))) {

            throw new OtpExpiredException("OTP expired");
        }

        if (otpVerification.getAttempts()
                >= OtpConstants.MAX_OTP_ATTEMPTS) {

            throw new InvalidOtpException(
                    "Maximum OTP attempts exceeded. Please request a new OTP.");
        }

        if (otpVerification.getOtp() == null
                || otpVerification.getOtp().isBlank()) {

            throw new InvalidOtpException(
                    "No active OTP found. Please request a new OTP.");
        }

        boolean matched = passwordEncoder.matches(
                enteredOtp,
                otpVerification.getOtp());

        if (!matched) {

            otpVerification.setAttempts(
                    otpVerification.getAttempts() + 1);

            otpRepository.saveAndFlush(otpVerification);

            if (otpVerification.getAttempts()
                    >= OtpConstants.MAX_OTP_ATTEMPTS) {

                throw new InvalidOtpException(
                        "Maximum OTP attempts exceeded. Please request a new OTP.");
            }

            throw new InvalidOtpException("Invalid OTP.");
        }

        otpVerification.setVerified(true);
        otpRepository.save(otpVerification);
    }

    private String getWaitMessage(long remainingSeconds) {

        if (remainingSeconds < 60) {
            return remainingSeconds + " seconds";
        }

        if (remainingSeconds < 3600) {

            long minutes = remainingSeconds / 60;
            long seconds = remainingSeconds % 60;

            if (seconds == 0) {
                return minutes + " minute" + (minutes > 1 ? "s" : "");
            }

            return minutes + " minute" + (minutes > 1 ? "s" : "")
                    + " " + seconds + " second"
                    + (seconds > 1 ? "s" : "");
        }

        long hours = remainingSeconds / 3600;
        long minutes = (remainingSeconds % 3600) / 60;

        if (minutes == 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        }

        return hours + " hour" + (hours > 1 ? "s" : "")
                + " " + minutes + " minute"
                + (minutes > 1 ? "s" : "");
    }
}