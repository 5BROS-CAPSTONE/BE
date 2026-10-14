package com.fivebros.backend.account.dto;

import java.util.Set;

import com.fivebros.backend.security.Role;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ApproveRegistrationRequest(
        @ArraySchema(minItems = 1, uniqueItems = true, schema = @Schema(example = "OPERATOR",
                allowableValues = {"MANAGER", "EXPERT", "OPERATOR", "TECHNICIAN", "ANALYST"}))
        @NotEmpty Set<@NotNull Role> roles
) {
}
