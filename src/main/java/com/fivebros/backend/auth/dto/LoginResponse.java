package com.fivebros.backend.auth.dto;

import java.time.Instant;

import com.fivebros.backend.account.dto.UserResponse;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt,
        UserResponse user
) {
    @Override
    public String toString() {
        return "LoginResponse[token=REDACTED]";
    }
}
