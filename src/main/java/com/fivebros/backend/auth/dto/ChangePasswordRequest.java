package com.fivebros.backend.auth.dto;

import com.fivebros.backend.common.validation.PasswordPolicy;
import com.fivebros.backend.common.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                maxLength = PasswordPolicy.MAX_UTF8_BYTES, description = "Mật khẩu hiện tại của chính tài khoản đang đăng nhập.")
        @NotBlank @Size(max = PasswordPolicy.MAX_UTF8_BYTES) String currentPassword,
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                minLength = PasswordPolicy.MIN_LENGTH, maxLength = PasswordPolicy.MAX_UTF8_BYTES,
                description = PasswordPolicy.DESCRIPTION)
        @NotBlank @ValidPassword String newPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[credentials=REDACTED]";
    }
}
