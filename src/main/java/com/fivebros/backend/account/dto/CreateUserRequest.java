package com.fivebros.backend.account.dto;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fivebros.backend.common.validation.PasswordPolicy;
import com.fivebros.backend.common.validation.ValidPassword;
import com.fivebros.backend.security.Role;

public record CreateUserRequest(
        @Schema(example = "manager@example.com") @NotBlank @Email @Size(max = 254)
        String email,

        @Schema(example = "Mission Manager") @NotBlank @Size(max = 120)
        String fullName,

        @Schema(
                format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                minLength = PasswordPolicy.MIN_LENGTH,
                maxLength = PasswordPolicy.MAX_UTF8_BYTES,
                description = PasswordPolicy.DESCRIPTION,
                example = "Your-Own-Strong-Password!")
        @NotBlank
        @ValidPassword
        String password,

        @Schema(
                example = "[\"MANAGER\"]",
                description = "Đúng một role. Xem BPMN S5 - Người dùng & Phân quyền / Phân quyền.")
        @NotNull
        @Size(min = 1, max = 1, message = "Tài khoản phải có đúng một vai trò")
        Set<@NotNull Role> roles) {
    @Override
    public String toString() {
        return "CreateUserRequest[credentials=REDACTED]";
    }
}
