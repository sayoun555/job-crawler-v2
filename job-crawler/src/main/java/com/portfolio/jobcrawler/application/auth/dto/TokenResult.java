package com.portfolio.jobcrawler.application.auth.dto;

public record TokenResult(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    public static TokenResult of(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResult(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
