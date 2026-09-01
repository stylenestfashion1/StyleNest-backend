package com.stylenest.stylenest_backend.security;

import java.security.Key;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.stylenest.stylenest_backend.enums.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretKey;

    // Admin session duration -- unchanged from before this token was
    // role-aware; kept separate from the customer duration below so
    // lengthening customer sessions never affects admin security.
    @Value("${app.jwt.expiration}")
    private long adminJwtExpiration;

    // Customers were being logged out roughly daily and re-prompted to sign
    // in, which was poor UX for a shopping site -- 7 days by default.
    @Value("${app.jwt.customer-expiration}")
    private long customerJwtExpiration;

    private SecretKey getSigningKey() {

        return Keys.hmacShaKeyFor(secretKey.getBytes());

    }

    public String generateToken(UserDetails userDetails, Role role) {

        long expirationMillis = role == Role.CUSTOMER ? customerJwtExpiration : adminJwtExpiration;

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSigningKey())
                .compact();

    }

    public String extractUsername(String token) {

        return extractAllClaims(token).getSubject();

    }

    public boolean isTokenValid(String token, UserDetails userDetails) {

        final String username = extractUsername(token);

        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);

    }

    private boolean isTokenExpired(String token) {

        return extractAllClaims(token)
                .getExpiration()
                .before(new Date());

    }

    private Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

    }

}