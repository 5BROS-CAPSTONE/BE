package com.fivebros.backend.area.dto;

import java.math.BigDecimal;
import com.fivebros.backend.area.AreaUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateFieldRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        Double referenceLatitude,
        Double referenceLongitude,
        @DecimalMin("0.000001") @Digits(integer = 18, fraction = 6)
        @Schema(description = "Only for fields without a boundary; omit to retain the existing declared area")
        BigDecimal declaredArea,
        AreaUnit areaUnit,
        @NotNull @Min(0) Long version) {
}
