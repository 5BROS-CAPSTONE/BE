package com.fivebros.backend.area;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import com.fivebros.backend.area.dto.*;
import com.fivebros.backend.common.PageResponse;
import com.fivebros.backend.common.error.ApiException;

@Service
@Transactional(readOnly = true)
public class AreaService {
    private final FarmRepository farms;
    private final FieldRepository fields;
    private final BoundaryService boundaries;
    private final ObjectMapper json;
    private final Clock clock;

    public AreaService(
            FarmRepository farms, FieldRepository fields, BoundaryService boundaries, ObjectMapper json, Clock clock) {
        this.farms = farms;
        this.fields = fields;
        this.boundaries = boundaries;
        this.json = json;
        this.clock = clock;
    }

    public PageResponse<FarmResponse> listFarms(String search, int page, int size) {
        return PageResponse.from(farms.searchWithFieldCount(search.strip(), paging(page, size)));
    }

    public FarmResponse getFarm(UUID id) {
        Farm farm = findFarm(id);
        return FarmResponse.from(farm, fields.countByFarmId(id));
    }

    @Transactional
    public FarmResponse createFarm(UUID actor, FarmRequest request) {
        return FarmResponse.from(
                farms.saveAndFlush(new Farm(actor, clock.instant(), request.name(), request.description())), 0);
    }

    @Transactional
    public FarmResponse updateFarm(UUID actor, UUID id, FarmRequest request) {
        Farm farm = findFarm(id);
        checkVersion(farm, request.version());
        farm.update(request.name(), request.description(), actor, clock.instant());
        farms.flush();
        return FarmResponse.from(farm, fields.countByFarmId(id));
    }

    public PageResponse<FieldResponse> listFields(UUID farmId, String search, int page, int size) {
        findFarm(farmId);
        return PageResponse.from(
                fields.findByFarmIdAndNameContainingIgnoreCase(farmId, search.strip(), paging(page, size))
                        .map(this::response));
    }

    public FieldResponse getField(UUID id) {
        return response(findField(id));
    }

    @Transactional
    public FieldResponse createField(UUID actor, UUID farmId, CreateFieldRequest request) {
        findFarm(farmId);
        validateCoordinates(request.referenceLatitude(), request.referenceLongitude(), "Reference point");
        BigDecimal declared = declaredArea(request.declaredArea(), request.areaUnit());
        Field field = new Field(farmId, actor, clock.instant());
        field.updateProfile(
                request.name(),
                request.description(),
                request.referenceLatitude(),
                request.referenceLongitude(),
                actor,
                clock.instant());
        field.declareArea(declared);
        if (request.boundary() != null) {
            applyBoundary(field, request.boundary(), request.boundarySource(), actor);
        } else {
            if (request.boundarySource() != null && request.boundarySource() != BoundarySource.NONE) {
                throw badRequest("INVALID_BOUNDARY", "boundarySource requires a boundary");
            }
            if (declared == null)
                throw badRequest("AREA_REQUIRED", "Supply a positive declared area or a valid boundary");
        }
        return response(fields.saveAndFlush(field));
    }

    @Transactional
    public FieldResponse updateField(UUID actor, UUID id, UpdateFieldRequest request) {
        Field field = findField(id);
        checkVersion(field, request.version());
        validateCoordinates(request.referenceLatitude(), request.referenceLongitude(), "Reference point");
        BigDecimal declared = declaredArea(request.declaredArea(), request.areaUnit());
        if (declared != null) {
            if (field.getAreaSource() == AreaSource.BOUNDARY) {
                throw badRequest(
                        "BOUNDARY_AREA_READ_ONLY",
                        "Area is calculated from the boundary; use the boundary endpoint to change it");
            }
            field.declareArea(declared);
        }
        field.updateProfile(
                request.name(),
                request.description(),
                request.referenceLatitude(),
                request.referenceLongitude(),
                actor,
                clock.instant());
        fields.flush();
        return response(field);
    }

    @Transactional
    public FieldResponse replaceBoundary(UUID actor, UUID id, BoundaryRequest request) {
        Field field = findField(id);
        checkVersion(field, request.version());
        applyBoundary(field, request.boundary(), request.boundarySource(), actor);
        fields.flush();
        return response(field);
    }

    public BoundaryPreview previewBoundary(BoundaryRequest request) {
        boundarySource(request.boundarySource());
        return boundaries.preview(request.boundary());
    }

    private void applyBoundary(Field field, PolygonGeometry boundary, BoundarySource source, UUID actor) {
        BoundarySource resolved = boundarySource(source);
        BoundaryPreview preview = boundaries.preview(boundary);
        field.replaceBoundary(json.writeValueAsString(preview.boundary()), resolved, preview, actor, clock.instant());
    }

    private BoundarySource boundarySource(BoundarySource source) {
        if (source == BoundarySource.NONE)
            throw badRequest("INVALID_BOUNDARY", "A polygon requires KML or MANUAL_COORDINATES as its source");
        return source == null ? BoundarySource.MANUAL_COORDINATES : source;
    }

    private BigDecimal declaredArea(BigDecimal amount, AreaUnit unit) {
        if (amount == null) {
            if (unit != null) throw badRequest("AREA_REQUIRED", "areaUnit requires declaredArea");
            return null;
        }
        BigDecimal m2 = unit == AreaUnit.HA ? amount.movePointRight(4) : amount;
        if (m2.signum() <= 0 || m2.compareTo(new BigDecimal("999999999999999999.999999")) > 0) {
            throw badRequest("INVALID_AREA", "Declared area is outside the supported m2 range");
        }
        return m2;
    }

    private void validateCoordinates(Double latitude, Double longitude, String label) {
        if (latitude != null || longitude != null) BoundaryService.validatePosition(latitude, longitude, label);
    }

    private void checkVersion(AreaRecord record, Long version) {
        if (version == null) throw badRequest("VERSION_REQUIRED", "Supply the latest version when updating");
        if (version != record.getVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Data has changed; reload before updating");
        }
    }

    private Farm findFarm(UUID id) {
        return farms.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND", "Farm was not found"));
    }

    private Field findField(UUID id) {
        return fields.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FIELD_NOT_FOUND", "Field was not found"));
    }

    private FieldResponse response(Field field) {
        return FieldResponse.from(
                field,
                field.getBoundaryGeojson() == null
                        ? null
                        : json.readValue(field.getBoundaryGeojson(), PolygonGeometry.class));
    }

    private PageRequest paging(int page, int size) {
        return PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
