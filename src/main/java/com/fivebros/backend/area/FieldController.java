package com.fivebros.backend.area;

import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fivebros.backend.area.dto.*;

@RestController
@RequestMapping("/api/v1/fields")
@Tag(
        name = "Agricultural areas - Fields",
        description = "AREA-02/03. MANAGER and ADMIN. WGS84, one exterior ring; no map drawing.")
public class FieldController {
    private final AreaService areas;
    private final KmlImportService kml;

    public FieldController(AreaService areas, KmlImportService kml) {
        this.areas = areas;
        this.kml = kml;
    }

    @GetMapping("/{fieldId}")
    @PreAuthorize("hasAuthority('area:read')")
    @Operation(
            summary = "Get field geometry, effective area and audit details",
            description =
                    "areaM2 and areaHa use the boundary when present. "
                            + "Without a boundary, perimeterM and computedAreaM2 are null; reference point does not imply a boundary.")
    public FieldResponse get(@PathVariable UUID fieldId) {
        return areas.getField(fieldId);
    }

    @PutMapping("/{fieldId}")
    @PreAuthorize("hasAuthority('area:update')")
    @Operation(
            summary = "Update field profile and manual area",
            description =
                    "Requires the current version. "
                            + "Omit declaredArea to retain it; supplying it for a field with a boundary is rejected. "
                            + "Null description/reference coordinates clear those profile values. Farm and boundary are preserved.")
    @ApiResponse(responseCode = "409", description = "VERSION_CONFLICT: reload before updating")
    public FieldResponse update(
            @PathVariable UUID fieldId,
            @Valid @RequestBody UpdateFieldRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return areas.updateField(UUID.fromString(jwt.getSubject()), fieldId, request);
    }

    @PostMapping("/boundary/preview")
    @PreAuthorize("hasAnyAuthority('area:create','area:update')")
    @Operation(
            summary = "Validate and measure a polygon without saving",
            description =
                    "GeoJSON [longitude,latitude]. "
                            + "Server closes the ring, normalizes orientation and calculates ellipsoidal WGS84 m2/m. No client-computed measurements are used.")
    public BoundaryPreview preview(@Valid @RequestBody BoundaryRequest request) {
        return areas.previewBoundary(request);
    }

    @PutMapping("/{fieldId}/boundary")
    @PreAuthorize("hasAuthority('area:update')")
    @Operation(
            summary = "Replace field boundary and recalculate measurements",
            description = "Requires version. "
                    + "Submit the selected KML preview polygon with boundarySource=KML, or manual coordinates. "
                    + "Server revalidates on save; an invalid replacement leaves stored data unchanged.")
    @ApiResponse(responseCode = "409", description = "VERSION_CONFLICT: reload before updating")
    public FieldResponse boundary(
            @PathVariable UUID fieldId, @Valid @RequestBody BoundaryRequest request, @AuthenticationPrincipal Jwt jwt) {
        return areas.replaceBoundary(UUID.fromString(jwt.getSubject()), fieldId, request);
    }

    @PostMapping(value = "/import-kml/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('area:create','area:update')")
    @Operation(
            summary = "Preview KML polygon candidates without saving",
            description =
                    "Upload multipart part 'file' (.kml only, default 5 MiB). "
                            + "Returns each polygon and its errors/warnings; choose a valid candidate and send its geometry to create/update. "
                            + "Holes, MultiGeometry, Point, LineString and KMZ are reported as unsupported. External XML resources are never loaded.")
    @ApiResponse(responseCode = "413", description = "KML file exceeds upload limit")
    public KmlPreviewResponse importKml(@RequestPart("file") MultipartFile file) {
        return kml.preview(file);
    }
}
