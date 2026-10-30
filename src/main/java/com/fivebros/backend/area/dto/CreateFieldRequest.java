package com.fivebros.backend.area.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fivebros.backend.area.AreaUnit;
import com.fivebros.backend.area.BoundarySource;

public record CreateFieldRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        Double referenceLatitude,
        Double referenceLongitude,

        @DecimalMin("0.000001")
        @Digits(integer = 18, fraction = 6)
        @Schema(description = "Positive declared area; required when boundary is absent", example = "1.5")
        BigDecimal declaredArea,

        @Schema(description = "Defaults to M2 when declaredArea is supplied", example = "HA")
        AreaUnit areaUnit,

        PolygonGeometry boundary,

        @Schema(description = "Defaults to MANUAL_COORDINATES with a boundary; NONE without one")
        BoundarySource boundarySource) {}
