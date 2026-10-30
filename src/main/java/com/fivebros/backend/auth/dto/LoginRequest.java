package com.fivebros.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fivebros.backend.common.validation.PasswordPolicy;

public record LoginRequest(
        @Schema(example = "admin@5bros.local") @NotBlank @Email @Size(max = 254)
        String email,

        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, example = "Your-Own-Strong-Password!")
        @NotBlank
        @Size(max = PasswordPolicy.MAX_UTF8_BYTES)
        String password) {
    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}
