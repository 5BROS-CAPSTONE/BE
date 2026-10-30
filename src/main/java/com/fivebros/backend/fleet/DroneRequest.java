package com.fivebros.backend.fleet;

import java.util.UUID;

import jakarta.validation.constraints.*;

import io.swagger.v3.oas.annotations.media.Schema;

public record DroneRequest(
        @NotBlank @Size(max = 100) String serial,
        @NotBlank @Size(max = 160) String name,
        @NotNull UUID uavProfileId,

        @Schema(description = "Required for PUT; omit for POST") @PositiveOrZero
        Long version) {}
