package com.portfolio.jobcrawler.global.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:default-secret-key-for-dev-must-be-at-least-256-bits-long!!}") String secret,
            @Value("${jwt.access-expiration:1800000}") long accessTokenValidityMs,
            @Value("${jwt.refresh-expiration:604800000}") long refreshTokenValidityMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public String createAccessToken(Long userId, String email, String role) {
        return buildToken(userId, email, role, accessTokenValidityMs, "access");
    }

    public String createRefreshToken(Long userId, String email, String role) {
        return buildToken(userId, email, role, refreshTokenValidityMs, "refresh");
    }

    private String buildToken(Long userId, String email, String role, long validityMs, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMs))
                .signWith(key)
                .compact();
    }

    /** @deprecated Use createAccessToken instead */
    public String createToken(Long userId, String email, String role) {
        return createAccessToken(userId, email, role);
    }

    public Long getUserId(String token) {
        return Long.parseLong(
                Jwts.parser().verifyWith(key).build()
                        .parseSignedClaims(token)
                        .getPayload().getSubject());
    }

    public String getRole(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload().get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT 만료: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("JWT 검증 실패: {}", e.getMessage());
        }
        return false;
    }

    public long getAccessTokenValidityInSeconds() {
        return accessTokenValidityMs / 1000;
    }

    public long getRefreshTokenValidityInSeconds() {
        return refreshTokenValidityMs / 1000;
    }

    /** @deprecated Use getAccessTokenValidityInSeconds */
    public long getTokenValidityInSeconds() {
        return accessTokenValidityMs / 1000;
    }

    public boolean isRefreshToken(String token) {
        try {
            String type = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload().get("type", String.class);
            return "refresh".equals(type);
        } catch (Exception e) {
            return false;
        }
    }
}
