package com.fivebros.backend.account.dto;

import com.fivebros.backend.common.validation.ValidPassword;
import com.fivebros.backend.common.validation.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String fullName,
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                minLength = PasswordPolicy.MIN_LENGTH, maxLength = PasswordPolicy.MAX_UTF8_BYTES,
                description = PasswordPolicy.DESCRIPTION + " Bỏ trường này hoặc gửi null để giữ mật khẩu hiện tại.")
        @ValidPassword String password
) {
    @Override
    public String toString() {
        return "UpdateUserRequest[credentials=REDACTED]";
    }
}
