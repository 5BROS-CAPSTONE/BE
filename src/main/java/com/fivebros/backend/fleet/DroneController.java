package com.fivebros.backend.fleet;

import java.net.URI;
import java.util.UUID;
import com.fivebros.backend.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/drones")
@Tag(name = "Fleet - Drones", description = "FLT-02/03. Physical drone inventory; TECHNICIAN/ADMIN write, MANAGER also reads.")
public class DroneController {
    private final FleetService fleet;

    public DroneController(FleetService fleet) { this.fleet = fleet; }

    @GetMapping
    @PreAuthorize("hasAuthority('drone:read')")
    @Operation(summary = "Search drones by name/serial and filter by status and UAV Profile")
    public PageResponse<DroneResponse> list(
            @RequestParam(defaultValue = "") @Size(max = 160) String search,
            @RequestParam(required = false) DroneStatus status,
            @RequestParam(required = false) UUID uavProfileId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return fleet.listDrones(search, status, uavProfileId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('drone:read')")
    @Operation(summary = "Get drone details including UAV Profile and battery integration availability")
    public DroneResponse get(@PathVariable UUID id) { return fleet.getDrone(id); }

    @PostMapping
    @PreAuthorize("hasAuthority('drone:create')")
    @Operation(summary = "Register a physical drone", description = "Requires an existing UAV Profile. Serial is unique ignoring case. Initial status is AVAILABLE; mission readiness requires future battery and planning checks.")
    @ApiResponse(responseCode = "201", description = "Drone registered")
    public ResponseEntity<DroneResponse> create(@Valid @RequestBody DroneRequest request, @AuthenticationPrincipal Jwt jwt) {
        DroneResponse response = fleet.createDrone(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/v1/drones/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('drone:update')")
    @Operation(summary = "Update serial, name or UAV Profile", description = "Requires current version. In-mission and retired drones cannot be edited. Status is managed separately, not accepted by this request.")
    @ApiResponse(responseCode = "409", description = "Version, serial or lifecycle conflict")
    public DroneResponse update(@PathVariable UUID id, @Valid @RequestBody DroneRequest request, @AuthenticationPrincipal Jwt jwt) {
        return fleet.updateDrone(UUID.fromString(jwt.getSubject()), id, request);
    }
}
