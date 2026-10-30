package com.fivebros.backend.fleet;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.fivebros.backend.common.PageResponse;

@Validated
@RestController
@RequestMapping("/api/v1/uav-profiles")
@Tag(
        name = "Fleet - UAV Profiles",
        description = "FLT-01. Shared model specifications; EXPERT/ADMIN write, MANAGER/TECHNICIAN also read.")
public class UavProfileController {
    private final FleetService fleet;

    public UavProfileController(FleetService fleet) {
        this.fleet = fleet;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('uav-profile:read')")
    @Operation(summary = "Search UAV Profiles by manufacturer or model")
    public PageResponse<UavProfileResponse> list(
            @RequestParam(defaultValue = "") @Size(max = 160) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return fleet.listProfiles(search, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('uav-profile:read')")
    @Operation(summary = "Read a UAV Profile with technical units and datasheet provenance")
    public UavProfileResponse get(@PathVariable UUID id) {
        return fleet.getProfile(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('uav-profile:create')")
    @Operation(
            summary = "Create a UAV Profile",
            description =
                    "Unknown technical values remain null. Manufacturer/model pair is unique ignoring case. No datasheet values are inferred.")
    @ApiResponse(responseCode = "201", description = "Profile created")
    public ResponseEntity<UavProfileResponse> create(
            @Valid @RequestBody UavProfileRequest request, @AuthenticationPrincipal Jwt jwt) {
        UavProfileResponse response = fleet.createProfile(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/v1/uav-profiles/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('uav-profile:update')")
    @Operation(
            summary = "Replace a UAV Profile",
            description =
                    "Requires current version. Optional values are replaced; null clears them. Future DSS must snapshot these values, never dereference mutable profiles for historical evaluations.")
    @ApiResponse(responseCode = "409", description = "Version or identity conflict")
    public UavProfileResponse update(
            @PathVariable UUID id, @Valid @RequestBody UavProfileRequest request, @AuthenticationPrincipal Jwt jwt) {
        return fleet.updateProfile(UUID.fromString(jwt.getSubject()), id, request);
    }
}
