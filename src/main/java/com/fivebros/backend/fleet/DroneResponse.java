package com.fivebros.backend.fleet;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record DroneResponse(
        UUID id, String serial, String name, DroneStatus status, UavProfileResponse uavProfile,
        BatteryInstallationResponse batteryInstallation,
        UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt, long version
) {
    @Schema(description = "BAT module is not implemented. integrated=false and batteryIds=null mean unknown, not zero installed batteries.")
    public record BatteryInstallationResponse(boolean integrated, List<UUID> batteryIds) { }

    static DroneResponse from(Drone drone) {
        return new DroneResponse(drone.id, drone.serial, drone.name, drone.status,
                UavProfileResponse.from(drone.uavProfile), new BatteryInstallationResponse(false, null),
                drone.createdBy, drone.updatedBy, drone.createdAt, drone.updatedAt, drone.version);
    }
}

