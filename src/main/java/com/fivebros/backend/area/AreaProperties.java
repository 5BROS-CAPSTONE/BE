package com.fivebros.backend.area;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.area")
public record AreaProperties(
        @DefaultValue("2000") @Min(3) @Max(100000) int maxVertices,
        @DefaultValue("5242880") @Min(1) @Max(52428800) int maxKmlBytes,
        @DefaultValue("100") @Min(1) @Max(1000) int maxKmlFeatures) {
}
