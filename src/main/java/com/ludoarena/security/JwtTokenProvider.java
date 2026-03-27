package com.ludoarena.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT Token Provider - Generates and validates JWT tokens.
 *
 * THREAD SAFETY:
 * - This is a Spring singleton bean, shared across all Tomcat request threads.
 * - It is THREAD-SAFE because:
 *   1. The secretKey and expirationMs fields are final (set once on initialization).
 *   2. Jwts.builder() and Jwts.parser() create new instances per call (not shared state).
 *   3. No mutable state is modified after initialization.
 * - Multiple HTTP threads can call generateToken() and validateToken() concurrently
 *   without any synchronization needed.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // Decode base64 secret into a SecretKey for HMAC-SHA256
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes())));
        this.expirationMs = expirationMs;
    }

    /**
     * Generate a JWT token for the given user ID.
     * Called from AuthService on successful login/signup.
     *
     * @param userId the authenticated user's ID
     * @return signed JWT token string
     */
    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extract user ID from a JWT token.
     * Called by JwtAuthFilter on every authenticated request.
     *
     * @param token the JWT token string
     * @return user ID extracted from token's subject claim
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    /**
     * Validate a JWT token (checks signature and expiration).
     *
     * @param token the JWT token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            // Token is invalid, expired, or malformed
            return false;
        }
    }
}
