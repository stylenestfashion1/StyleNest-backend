package com.stylenest.stylenest_backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.admin.AdminDiscountOfferResponse;
import com.stylenest.stylenest_backend.dto.admin.AdminDiscountOfferSearchRequest;
import com.stylenest.stylenest_backend.dto.admin.AdminDiscountQrResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountClaimRequest;
import com.stylenest.stylenest_backend.dto.discount.DiscountClaimResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountRangeResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountSessionResponse;
import com.stylenest.stylenest_backend.entity.DiscountOffer;
import com.stylenest.stylenest_backend.entity.DiscountSlot;
import com.stylenest.stylenest_backend.enums.DiscountOfferStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.DiscountQrTokenInvalidException;
import com.stylenest.stylenest_backend.exception.DiscountSessionInvalidException;
import com.stylenest.stylenest_backend.exception.MobileNumberAlreadyUsedException;
import com.stylenest.stylenest_backend.mapper.DiscountMapper;
import com.stylenest.stylenest_backend.repository.DiscountOfferRepository;
import com.stylenest.stylenest_backend.repository.DiscountSlotRepository;
import com.stylenest.stylenest_backend.service.DiscountConfigService;
import com.stylenest.stylenest_backend.service.DiscountOfferService;
import com.stylenest.stylenest_backend.specification.DiscountOfferSpecification;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

/**
 * The in-store QR discount flow, end to end:
 *
 *   permanent QR (secret token) -> verifyQrToken -> short-lived offer
 *   session token -> customer enters name+mobile -> claim (re-validates the
 *   session, then is the sole authority for the generated discount).
 *
 * The offer session is a small, self-issued JWT (reusing the app's existing
 * signing key, app.jwt.secret) carrying a "purpose" claim distinct from a
 * real user session -- it is never accepted by JwtAuthenticationFilter as a
 * login token (different claim shape, and it is never looked up as a user).
 * This deliberately avoids a separate DB-backed session table: the JWT's
 * own signature and expiry are the only state needed, and it can never be
 * forged without the same secret used to sign real user JWTs.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DiscountOfferServiceImpl implements DiscountOfferService {

    private static final String SESSION_PURPOSE_CLAIM = "purpose";
    private static final String SESSION_PURPOSE_VALUE = "discount_offer";
    private static final long SESSION_TTL_MILLIS = 20 * 60 * 1000L; // 20 minutes

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.discount.qr-secret-token}")
    private String qrSecretToken;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final DiscountOfferRepository discountOfferRepository;
    private final DiscountSlotRepository discountSlotRepository;
    private final DiscountMapper discountMapper;
    private final DiscountConfigService discountConfigService;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public DiscountSessionResponse verifyQrToken(String rawQrToken) {

        if (rawQrToken == null || rawQrToken.isBlank() || !constantTimeEquals(rawQrToken.trim(), qrSecretToken)) {
            throw new DiscountQrTokenInvalidException("This QR code is not valid.");
        }

        Date now = new Date();
        Date expiry = new Date(now.getTime() + SESSION_TTL_MILLIS);

        String sessionToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim(SESSION_PURPOSE_CLAIM, SESSION_PURPOSE_VALUE)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();

        return DiscountSessionResponse.builder()
                .sessionToken(sessionToken)
                .expiresAt(LocalDateTime.ofInstant(expiry.toInstant(), ZoneId.systemDefault()))
                .build();
    }

    @Override
    public DiscountClaimResponse claim(String sessionToken, DiscountClaimRequest request) {

        requireValidSession(sessionToken);

        String customerName = normalizeName(request.getCustomerName());
        String mobileNumber = normalizeMobile(request.getMobileNumber());

        if (discountOfferRepository.existsByMobileNumber(mobileNumber)) {
            throw alreadyUsed();
        }

        int discountPercentage = selectWeightedDiscount();

        DiscountOffer offer = DiscountOffer.builder()
                .customerName(customerName)
                .mobileNumber(mobileNumber)
                .discountPercentage(discountPercentage)
                .status(DiscountOfferStatus.REDEEMED)
                .generatedAt(LocalDateTime.now())
                .build();

        try {
            offer = discountOfferRepository.saveAndFlush(offer);
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent requests for the same mobile number both passed
            // the existsByMobileNumber check above -- the database's unique
            // constraint on mobile_number is the real, final authority, and
            // exactly one of the two inserts can ever succeed.
            throw alreadyUsed();
        }

        return discountMapper.toClaimResponse(offer);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminDiscountOfferResponse> searchOffers(AdminDiscountOfferSearchRequest request) {

        Sort sort = Sort.by(Sort.Direction.fromString(request.getDirection()), request.getSortBy());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSizePerPage(), sort);

        return discountOfferRepository.findAll(DiscountOfferSpecification.search(request), pageable)
                .map(discountMapper::toAdminResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDiscountQrResponse getQrInfo() {

        String url = frontendUrl.replaceAll("/+$", "") + "/special-offer/" + qrSecretToken;

        DiscountRangeResponse range = discountConfigService.getDisplayRange();

        return AdminDiscountQrResponse.builder()
                .url(url)
                .minDiscountPercentage(range.getMinDiscountPercentage())
                .maxDiscountPercentage(range.getMaxDiscountPercentage())
                .build();
    }

    private void requireValidSession(String sessionToken) {

        if (sessionToken == null || sessionToken.isBlank()) {
            throw new DiscountSessionInvalidException("Your offer session has expired. Please scan the QR code again.");
        }

        try {
            var claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(sessionToken)
                    .getPayload();

            if (!SESSION_PURPOSE_VALUE.equals(claims.get(SESSION_PURPOSE_CLAIM, String.class))) {
                throw new DiscountSessionInvalidException("Your offer session has expired. Please scan the QR code again.");
            }

        } catch (JwtException | IllegalArgumentException ex) {
            throw new DiscountSessionInvalidException("Your offer session has expired. Please scan the QR code again.");
        }
    }

    private String normalizeName(String rawName) {

        if (rawName == null) {
            throw new BadRequestException("Name is required.");
        }

        String trimmed = rawName.trim().replaceAll("\\s+", " ");

        if (trimmed.length() < 2) {
            throw new BadRequestException("Name must be at least 2 characters.");
        }

        return trimmed;
    }

    private String normalizeMobile(String rawMobile) {

        if (rawMobile == null) {
            throw new BadRequestException("Mobile number is required.");
        }

        // Strip everything but digits, then drop a leading "91" country
        // code (with or without a "+") so "+91 98765 43210", "9198765
        // 43210" and "9876543210" all normalize to the same 10 digits.
        String digitsOnly = rawMobile.replaceAll("\\D", "");

        if (digitsOnly.length() == 12 && digitsOnly.startsWith("91")) {
            digitsOnly = digitsOnly.substring(2);
        }

        if (!digitsOnly.matches("^[6-9]\\d{9}$")) {
            throw new BadRequestException("Enter a valid 10-digit Indian mobile number.");
        }

        return digitsOnly;
    }

    private int selectWeightedDiscount() {

        List<DiscountSlot> slots = discountSlotRepository.findAllByOrderByDiscountPercentageAsc();

        if (slots.isEmpty()) {
            throw new BadRequestException("Discount configuration is not set up yet. Please contact the shop.");
        }

        int roll = 1 + SECURE_RANDOM.nextInt(100); // 1-100 inclusive
        int cumulative = 0;

        for (DiscountSlot slot : slots) {
            cumulative += slot.getProbabilityPercentage();
            if (roll <= cumulative) {
                return slot.getDiscountPercentage();
            }
        }

        // Only reached if the configured probabilities sum to under 100
        // (shouldn't happen -- DiscountConfigServiceImpl enforces exactly
        // 100 on every save) -- fall back to the last slot rather than
        // ever failing the customer's claim.
        return slots.get(slots.size() - 1).getDiscountPercentage();
    }

    private MobileNumberAlreadyUsedException alreadyUsed() {
        return new MobileNumberAlreadyUsedException(
                "You have already generated your StyleNest discount. Each mobile number can claim this offer only once.");
    }

    private boolean constantTimeEquals(String a, String b) {

        if (a == null || b == null) {
            return false;
        }

        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
