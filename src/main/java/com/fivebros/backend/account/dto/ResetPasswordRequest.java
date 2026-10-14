package com.fivebros.backend.account.dto;

import com.fivebros.backend.common.validation.PasswordPolicy;
import com.fivebros.backend.common.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                minLength = PasswordPolicy.MIN_LENGTH, maxLength = PasswordPolicy.MAX_UTF8_BYTES,
                description = PasswordPolicy.DESCRIPTION)
        @NotBlank @ValidPassword String password
) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[credentials=REDACTED]";
    }
}
