package com.fivebros.backend.account.dto;

import com.fivebros.backend.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String fullName,
        @ValidPassword String password
) {
    @Override
    public String toString() {
        return "UpdateUserRequest[credentials=REDACTED]";
    }
}
