package com.fivebros.backend.area.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record FarmRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,

        @Min(0) @Schema(description = "Required for PUT; use the latest response version. Omit for POST.")
        Long version) {}
