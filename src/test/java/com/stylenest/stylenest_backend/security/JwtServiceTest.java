package com.stylenest.stylenest_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import com.stylenest.stylenest_backend.enums.Role;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private static final String SECRET = "test-only-jwt-signing-secret-not-used-in-production-0123456789";
    private static final long ADMIN_EXPIRATION_MS = 3_600_000L; // 1 hour -- unchanged legacy duration
    private static final long CUSTOMER_EXPIRATION_MS = 604_800_000L; // 7 days

    private JwtService jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "adminJwtExpiration", ADMIN_EXPIRATION_MS);
        ReflectionTestUtils.setField(jwtService, "customerJwtExpiration", CUSTOMER_EXPIRATION_MS);

        userDetails = new org.springframework.security.core.userdetails.User(
                "customer@example.com", "hashed", List.of());
    }

    @Test
    void generateToken_customer_expiresApproximatelySevenDaysLater() {

        long before = System.currentTimeMillis();

        String token = jwtService.generateToken(userDetails, Role.CUSTOMER);

        Date expiration = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();

        long actualDurationMs = expiration.getTime() - before;

        assertThat(actualDurationMs)
                .isCloseTo(CUSTOMER_EXPIRATION_MS, org.assertj.core.data.Offset.offset(5_000L));
    }

    @Test
    void generateToken_admin_usesTheSeparateAdminExpirationNotTheCustomerOne() {

        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                "admin@example.com", "hashed", List.of());

        long before = System.currentTimeMillis();

        String token = jwtService.generateToken(adminDetails, Role.ADMIN);

        Date expiration = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();

        long actualDurationMs = expiration.getTime() - before;

        assertThat(actualDurationMs)
                .isCloseTo(ADMIN_EXPIRATION_MS, org.assertj.core.data.Offset.offset(5_000L));
        assertThat(actualDurationMs).isLessThan(CUSTOMER_EXPIRATION_MS);
    }

    @Test
    void isTokenValid_acceptsFreshlyIssuedToken() {

        String token = jwtService.generateToken(userDetails, Role.CUSTOMER);

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_rejectsExpiredToken() {

        // Built with the same secret/subject but an expiration already in
        // the past -- simulates a customer token from more than 7 days ago.
        String expiredToken = Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();

        org.junit.jupiter.api.Assertions.assertThrows(
                io.jsonwebtoken.ExpiredJwtException.class,
                () -> jwtService.isTokenValid(expiredToken, userDetails));
    }

    @Test
    void extractUsername_returnsTheTokenSubject() {

        String token = jwtService.generateToken(userDetails, Role.CUSTOMER);

        assertThat(jwtService.extractUsername(token)).isEqualTo("customer@example.com");
    }
}
