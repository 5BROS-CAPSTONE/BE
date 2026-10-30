package com.fivebros.backend.account.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.fivebros.backend.account.AccountStatus;
import com.fivebros.backend.account.UserAccount;
import com.fivebros.backend.security.Role;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        Set<Role> roles,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
