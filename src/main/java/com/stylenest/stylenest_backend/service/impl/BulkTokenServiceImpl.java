package com.stylenest.stylenest_backend.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.bulk.BulkAccessTokenSummaryResponse;
import com.stylenest.stylenest_backend.dto.bulk.BulkAccessValidateResponse;
import com.stylenest.stylenest_backend.entity.BulkAccessToken;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.exception.BulkAccessRateLimitedException;
import com.stylenest.stylenest_backend.exception.BulkTokenInvalidException;
import com.stylenest.stylenest_backend.exception.BulkTokenRevokedException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.BulkAccessTokenMapper;
import com.stylenest.stylenest_backend.repository.BulkAccessTokenRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.service.BulkTokenService;

import lombok.RequiredArgsConstructor;

/**
 * Permanent-token model: exactly 10 owner-managed access codes, no expiry.
 * See BulkAccessToken's class javadoc for the plaintext-storage rationale.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BulkTokenServiceImpl implements BulkTokenService {

    public static final int PERMANENT_TOKEN_COUNT = 10;

    private static final String TOKEN_PREFIX = "STNEST-";
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 7;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Brute-force protection on the public validate endpoint: at most 8
    // attempts per IP per 15-minute window. In-memory and per-instance --
    // acceptable for a single-VPS deployment. Especially important now that
    // there are only 10 permanent valid codes in existence (never
    // regenerated), so a determined guesser gets unlimited time -- this
    // caps their attempt rate regardless.
    private static final int MAX_ATTEMPTS_PER_WINDOW = 8;
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;
    private final ConcurrentHashMap<String, RateWindow> attemptsByIp = new ConcurrentHashMap<>();

    private final BulkAccessTokenRepository bulkAccessTokenRepository;
    private final BulkAccessTokenMapper bulkAccessTokenMapper;
    private final UserRepository userRepository;

    @Override
    public List<BulkAccessTokenSummaryResponse> ensureTenPermanentTokensProvisioned() {

        long existing = bulkAccessTokenRepository.count();

        User admin = getCurrentAdminOrNull();

        for (long i = existing; i < PERMANENT_TOKEN_COUNT; i++) {

            BulkAccessToken token = BulkAccessToken.builder()
                    .token(generateUniquePlaintextToken())
                    .createdAt(LocalDateTime.now())
                    .active(true)
                    .createdByAdmin(admin)
                    .build();

            bulkAccessTokenRepository.save(token);
        }

        return listTokens();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BulkAccessTokenSummaryResponse> listTokens() {

        return bulkAccessTokenRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .map(bulkAccessTokenMapper::toSummaryResponse)
                .toList();
    }

    @Override
    public void revokeToken(Long id) {

        BulkAccessToken token = findById(id);

        token.setActive(false);
        token.setRevokedAt(LocalDateTime.now());

        bulkAccessTokenRepository.save(token);
    }

    @Override
    public void reactivateToken(Long id) {

        BulkAccessToken token = findById(id);

        token.setActive(true);
        token.setRevokedAt(null);

        bulkAccessTokenRepository.save(token);
    }

    @Override
    public void unassignToken(Long id) {

        BulkAccessToken token = findById(id);

        token.setAssignedCustomerName(null);
        token.setAssignedCustomerEmail(null);
        token.setAssignedCustomerPhone(null);
        token.setFirstUsedAt(null);
        token.setLastUsedAt(null);

        bulkAccessTokenRepository.save(token);
    }

    @Override
    public BulkAccessTokenSummaryResponse reissueToken(Long id) {

        BulkAccessToken token = findById(id);

        token.setToken(generateUniquePlaintextToken());
        token.setActive(true);
        token.setRevokedAt(null);
        token.setAssignedCustomerName(null);
        token.setAssignedCustomerEmail(null);
        token.setAssignedCustomerPhone(null);
        token.setFirstUsedAt(null);
        token.setLastUsedAt(null);

        token = bulkAccessTokenRepository.save(token);

        return bulkAccessTokenMapper.toSummaryResponse(token);
    }

    @Override
    public BulkAccessValidateResponse validateForCustomer(String plaintextToken, String clientIp) {

        enforceRateLimit(clientIp);

        BulkAccessToken token = resolveActiveToken(plaintextToken);

        token.setLastUsedAt(LocalDateTime.now());

        bulkAccessTokenRepository.save(token);

        return BulkAccessValidateResponse.builder().valid(true).build();
    }

    @Override
    @Transactional(readOnly = true)
    public BulkAccessToken resolveActiveToken(String plaintextToken) {

        if (plaintextToken == null || plaintextToken.isBlank()) {
            throw new BulkTokenInvalidException("Please check your access code or contact the shop.");
        }

        BulkAccessToken token = bulkAccessTokenRepository.findByToken(plaintextToken.trim().toUpperCase())
                .orElseThrow(() -> new BulkTokenInvalidException(
                        "Please check your access code or contact the shop."));

        if (!Boolean.TRUE.equals(token.getActive())) {
            throw new BulkTokenRevokedException(
                    "This access code is no longer active. Please contact the shop.");
        }

        return token;
    }

    private void enforceRateLimit(String clientIp) {

        String key = clientIp == null ? "unknown" : clientIp;

        RateWindow window = attemptsByIp.computeIfAbsent(key, k -> new RateWindow());

        long now = System.currentTimeMillis();

        synchronized (window) {

            if (now - window.windowStart > WINDOW_MILLIS) {
                window.windowStart = now;
                window.count.set(0);
            }

            if (window.count.incrementAndGet() > MAX_ATTEMPTS_PER_WINDOW) {
                throw new BulkAccessRateLimitedException(
                        "Too many attempts. Please wait a while before trying again.");
            }
        }
    }

    private String generateUniquePlaintextToken() {

        String candidate;

        do {
            candidate = generatePlaintextToken();
        } while (bulkAccessTokenRepository.existsByToken(candidate));

        return candidate;
    }

    private String generatePlaintextToken() {

        StringBuilder sb = new StringBuilder(TOKEN_PREFIX);

        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(SECURE_RANDOM.nextInt(CODE_CHARS.length())));
        }

        return sb.toString();
    }

    private BulkAccessToken findById(Long id) {

        return bulkAccessTokenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Access token not found."));
    }

    /**
     * Startup provisioning has no authenticated HTTP request/admin behind
     * it, so this deliberately returns null there instead of throwing --
     * unlike getCurrentAdmin-style helpers elsewhere in the codebase that
     * always run inside an authenticated admin request.
     */
    private User getCurrentAdminOrNull() {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    private static class RateWindow {
        volatile long windowStart = System.currentTimeMillis();
        final AtomicInteger count = new AtomicInteger(0);
    }
}
