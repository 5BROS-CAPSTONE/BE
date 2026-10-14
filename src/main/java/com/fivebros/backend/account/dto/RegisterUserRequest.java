package com.fivebros.backend.account.dto;

import com.fivebros.backend.common.validation.ValidPassword;
import com.fivebros.backend.common.validation.PasswordPolicy;
import com.fivebros.backend.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @Schema(example = "pilot@example.com") @NotBlank @Email @Size(max = 254) String email,
        @Schema(example = "Nguyen Van A") @NotBlank @Size(max = 120) String fullName,
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                minLength = PasswordPolicy.MIN_LENGTH, maxLength = PasswordPolicy.MAX_UTF8_BYTES,
                description = PasswordPolicy.DESCRIPTION, example = "Your-Own-Strong-Password!")
        @NotBlank @ValidPassword String password,
        @Schema(description = "Role mong muốn; Admin quyết định role chính thức.", example = "OPERATOR",
                allowableValues = {"MANAGER", "EXPERT", "OPERATOR", "TECHNICIAN", "ANALYST"})
        @NotNull Role requestedRole
) {
    @Override
    public String toString() {
        return "RegisterUserRequest[credentials=REDACTED]";
    }
}
