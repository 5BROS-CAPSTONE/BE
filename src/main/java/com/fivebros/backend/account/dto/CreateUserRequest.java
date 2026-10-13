package com.fivebros.backend.account.dto;

import java.util.Set;

import com.fivebros.backend.common.validation.ValidPassword;
import com.fivebros.backend.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @ValidPassword String password,
        @NotEmpty Set<@NotNull Role> roles
) {
    @Override
    public String toString() {
        return "CreateUserRequest[credentials=REDACTED]";
    }
}
