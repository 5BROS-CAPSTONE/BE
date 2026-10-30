package com.fivebros.backend.fleet;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fixed units; unknown values remain null, never inferred. Battery Ah/Wh are per battery.")
public record ProfileSpecifications(
        @Schema(description = "Khối lượng rỗng (kg)") @Digits(integer = 12, fraction = 6) @Positive
        BigDecimal emptyWeightKg,

        @Schema(description = "MTOW (kg)") @Digits(integer = 12, fraction = 6) @Positive
        BigDecimal maxTakeoffWeightKg,

        @Schema(description = "Tải trọng tối đa (kg)") @Digits(integer = 12, fraction = 6) @PositiveOrZero
        BigDecimal maxPayloadKg,

        @Schema(description = "Điện lượng danh định mỗi pin (Ah)") @Digits(integer = 12, fraction = 6) @Positive
        BigDecimal batteryCapacityAh,

        @Schema(description = "Năng lượng danh định mỗi pin (Wh)") @Digits(integer = 12, fraction = 6) @Positive
        BigDecimal batteryEnergyWh,

        @Schema(description = "Sức chịu gió tối đa (m/s)") @Digits(integer = 12, fraction = 6) @PositiveOrZero
        BigDecimal maxWindSpeedMps,

        @Schema(description = "Nhiệt độ tối thiểu (°C)") @Digits(integer = 12, fraction = 6) @DecimalMin("-273.15")
        BigDecimal minOperatingTemperatureC,

        @Schema(description = "Nhiệt độ tối đa (°C)") @Digits(integer = 12, fraction = 6) @DecimalMin("-273.15")
        BigDecimal maxOperatingTemperatureC,

        @Schema(description = "Tốc độ bay tối đa (m/s)") @Digits(integer = 12, fraction = 6) @Positive
        BigDecimal maxSpeedMps,

        @Schema(description = "Thời gian bay tối đa theo điều kiện đo (phút)")
        @Digits(integer = 12, fraction = 6)
        @Positive
        BigDecimal maxFlightTimeMinutes,

        @Schema(description = "Năng lượng dự phòng toàn drone (Wh); thông số bổ sung, không có trong sheet")
        @Digits(integer = 12, fraction = 6)
        @PositiveOrZero
        BigDecimal reserveEnergyWh,

        @Schema(description = "Mức tiêu hao toàn drone (Wh/km); thông số bổ sung, không suy ra từ thời gian bay")
        @Digits(integer = 12, fraction = 6)
        @Positive
        BigDecimal energyConsumptionWhPerKm,

        @Schema(description = "Số pin yêu cầu trên một drone (cái)") @NotNull @Positive
        Integer batteryCount) {}
