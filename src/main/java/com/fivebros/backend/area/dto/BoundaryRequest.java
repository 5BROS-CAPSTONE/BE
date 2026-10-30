package com.fivebros.backend.area.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fivebros.backend.area.BoundarySource;

public record BoundaryRequest(
        @NotNull PolygonGeometry boundary,

        @Schema(description = "KML or MANUAL_COORDINATES (default); NONE is not accepted")
        BoundarySource boundarySource,

        @Min(0) @Schema(description = "Required for saving a replacement boundary; omitted for preview")
        Long version) {}
