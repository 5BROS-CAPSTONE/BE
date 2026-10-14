package com.fivebros.backend.account.dto;

import com.fivebros.backend.account.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String fullName,
        @Schema(description = "Bỏ trường này để giữ trạng thái. DISABLED khóa đăng nhập; ACTIVE kích hoạt tài khoản đã được cấp role.")
        AccountStatus status
) {
    @Override
    public String toString() {
        return "UpdateUserRequest[status=" + status + ", profile=REDACTED]";
    }
}
