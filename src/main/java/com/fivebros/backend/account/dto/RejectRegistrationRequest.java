package com.fivebros.backend.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRegistrationRequest(
        @Schema(example = "Không xác minh được thông tin người đăng ký") @NotBlank @Size(max = 500) String reason
) {
}
