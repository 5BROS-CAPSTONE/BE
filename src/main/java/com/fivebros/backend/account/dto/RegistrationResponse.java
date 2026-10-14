package com.fivebros.backend.account.dto;

import java.time.Instant;
import java.util.UUID;

import com.fivebros.backend.account.AccountStatus;
import com.fivebros.backend.account.UserAccount;
import com.fivebros.backend.security.Role;

public record RegistrationResponse(UUID id, String email, String fullName, Role requestedRole,
                                   AccountStatus status, Instant createdAt) {
    public static RegistrationResponse from(UserAccount user) {
        return new RegistrationResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRequestedRole(), user.getStatus(), user.getCreatedAt());
    }
}
