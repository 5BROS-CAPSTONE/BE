package com.fivebros.backend.area;

import java.net.URI;
import java.util.UUID;
import com.fivebros.backend.area.dto.*;
import com.fivebros.backend.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/farms")
@Tag(name = "Agricultural areas - Farms", description = "AREA-01/02. MANAGER and ADMIN; shared internal operating unit.")
public class FarmController {
    private final AreaService areas;

    public FarmController(AreaService areas) { this.areas = areas; }

    @GetMapping
    @PreAuthorize("hasAuthority('area:read')")
    @Operation(summary = "Search farms by name with pagination")
    public PageResponse<FarmResponse> list(
            @RequestParam(defaultValue = "") @Size(max = 160) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return areas.listFarms(search, page, size);
    }

    @GetMapping("/{farmId}")
    @PreAuthorize("hasAuthority('area:read')")
    @Operation(summary = "Get farm details")
    public FarmResponse get(@PathVariable UUID farmId) { return areas.getFarm(farmId); }

    @PostMapping
    @PreAuthorize("hasAuthority('area:create')")
    @Operation(summary = "Create a farm", description = "Description is optional. Audit actor comes from the authenticated account.")
    @ApiResponse(responseCode = "201", description = "Farm created")
    public ResponseEntity<FarmResponse> create(@Valid @RequestBody FarmRequest request, @AuthenticationPrincipal Jwt jwt) {
        FarmResponse response = areas.createFarm(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/v1/farms/" + response.id())).body(response);
    }

    @PutMapping("/{farmId}")
    @PreAuthorize("hasAuthority('area:update')")
    @Operation(summary = "Update farm profile", description = "Requires the current version. Optional profile values are replaced; null clears them.")
    @ApiResponse(responseCode = "409", description = "VERSION_CONFLICT: reload before updating")
    public FarmResponse update(@PathVariable UUID farmId, @Valid @RequestBody FarmRequest request, @AuthenticationPrincipal Jwt jwt) {
        return areas.updateFarm(UUID.fromString(jwt.getSubject()), farmId, request);
    }

    @GetMapping("/{farmId}/fields")
    @PreAuthorize("hasAuthority('area:read')")
    @Operation(summary = "Search fields/plots belonging to a farm")
    public PageResponse<FieldResponse> fields(@PathVariable UUID farmId,
            @RequestParam(defaultValue = "") @Size(max = 160) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return areas.listFields(farmId, search, page, size);
    }

    @PostMapping("/{farmId}/fields")
    @PreAuthorize("hasAuthority('area:create')")
    @Operation(summary = "Create a field/plot under a farm", description = "Supply declaredArea (M2 or HA) or a WGS84 Polygon. "
            + "With a boundary, area and perimeter are always recalculated by the server. "
            + "A declared area is retained separately for comparison. Reference point is optional and must be supplied as a pair.")
    @ApiResponse(responseCode = "201", description = "Field created")
    public ResponseEntity<FieldResponse> createField(@PathVariable UUID farmId,
            @Valid @RequestBody CreateFieldRequest request, @AuthenticationPrincipal Jwt jwt) {
        FieldResponse response = areas.createField(UUID.fromString(jwt.getSubject()), farmId, request);
        return ResponseEntity.created(URI.create("/api/v1/fields/" + response.id())).body(response);
    }
}
