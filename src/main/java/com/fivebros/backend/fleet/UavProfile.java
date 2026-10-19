package com.fivebros.backend.fleet;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import jakarta.persistence.*;

@Entity
@Table(name = "uav_profiles")
public class UavProfile extends FleetRecord {
    @Column(nullable = false, length = 100)
    String manufacturer;
    @Column(nullable = false, length = 160)
    String modelName;
    @Column(nullable = false, unique = true, length = 261)
    String identityKey;
    @Column(name = "empty_weight_kg", precision = 18, scale = 6)
    BigDecimal emptyWeightKg;
    @Column(name = "max_takeoff_weight_kg", precision = 18, scale = 6)
    BigDecimal maxTakeoffWeightKg;
    @Column(name = "max_payload_kg", precision = 18, scale = 6)
    BigDecimal maxPayloadKg;
    @Column(name = "battery_capacity_ah", precision = 18, scale = 6)
    BigDecimal batteryCapacityAh;
    @Column(name = "battery_energy_wh", precision = 18, scale = 6)
    BigDecimal batteryEnergyWh;
    @Column(name = "max_wind_speed_mps", precision = 18, scale = 6)
    BigDecimal maxWindSpeedMps;
    @Column(name = "min_operating_temperature_c", precision = 18, scale = 6)
    BigDecimal minOperatingTemperatureC;
    @Column(name = "max_operating_temperature_c", precision = 18, scale = 6)
    BigDecimal maxOperatingTemperatureC;
    @Column(name = "max_speed_mps", precision = 18, scale = 6)
    BigDecimal maxSpeedMps;
    @Column(name = "max_flight_time_minutes", precision = 18, scale = 6)
    BigDecimal maxFlightTimeMinutes;
    @Column(name = "reserve_energy_wh", precision = 18, scale = 6)
    BigDecimal reserveEnergyWh;
    @Column(name = "energy_consumption_wh_per_km", precision = 18, scale = 6)
    BigDecimal energyConsumptionWhPerKm;
    @Column(nullable = false)
    Integer batteryCount;
    @ElementCollection
    @org.hibernate.annotations.BatchSize(size = 100)
    @CollectionTable(name = "uav_profile_capabilities", joinColumns = @JoinColumn(name = "uav_profile_id"))
    @Column(name = "capability", nullable = false, length = 64)
    @OrderBy
    Set<String> capabilities = new TreeSet<>();
    @Column(length = 2000)
    String sourceUrl;
    LocalDate sourceAccessedOn;
    @Column(length = 160)
    String sourceRecordedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    ProfileDataStatus dataStatus;
    @Column(length = 8000)
    String notes;

    protected UavProfile() { }

    UavProfile(UUID actor, Instant now, UavProfileRequest request) {
        super(actor, now);
        update(request, actor, now);
    }

    void update(UavProfileRequest request, UUID actor, Instant now) {
        manufacturer = request.manufacturer().strip();
        modelName = request.modelName().strip();
        identityKey = identityKey(manufacturer, modelName);
        ProfileSpecifications specs = request.specifications();
        emptyWeightKg = specs.emptyWeightKg();
        maxTakeoffWeightKg = specs.maxTakeoffWeightKg();
        maxPayloadKg = specs.maxPayloadKg();
        batteryCapacityAh = specs.batteryCapacityAh();
        batteryEnergyWh = specs.batteryEnergyWh();
        maxWindSpeedMps = specs.maxWindSpeedMps();
        minOperatingTemperatureC = specs.minOperatingTemperatureC();
        maxOperatingTemperatureC = specs.maxOperatingTemperatureC();
        maxSpeedMps = specs.maxSpeedMps();
        maxFlightTimeMinutes = specs.maxFlightTimeMinutes();
        reserveEnergyWh = specs.reserveEnergyWh();
        energyConsumptionWhPerKm = specs.energyConsumptionWhPerKm();
        batteryCount = specs.batteryCount();
        capabilities.clear();
        request.capabilities().forEach(value -> capabilities.add(value.toUpperCase(Locale.ROOT)));
        sourceUrl = clean(request.sourceUrl());
        sourceAccessedOn = request.sourceAccessedOn();
        sourceRecordedBy = clean(request.sourceRecordedBy());
        dataStatus = request.dataStatus() == null ? ProfileDataStatus.UNVERIFIED : request.dataStatus();
        notes = clean(request.notes());
        touch(actor, now);
    }

    static String identityKey(String manufacturer, String modelName) {
        return manufacturer.strip().toLowerCase(Locale.ROOT) + "\n" + modelName.strip().toLowerCase(Locale.ROOT);
    }

    static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    ProfileSpecifications specifications() {
        return new ProfileSpecifications(emptyWeightKg, maxTakeoffWeightKg, maxPayloadKg, batteryCapacityAh, batteryEnergyWh, maxWindSpeedMps, minOperatingTemperatureC, maxOperatingTemperatureC, maxSpeedMps, maxFlightTimeMinutes, reserveEnergyWh, energyConsumptionWhPerKm, batteryCount);
    }
}

