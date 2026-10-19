package com.fivebros.backend.area.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.fivebros.backend.area.AreaSource;
import com.fivebros.backend.area.BoundarySource;
import com.fivebros.backend.area.Field;

public record FieldResponse(UUID id, UUID farmId, String name, String description,
                            Double referenceLatitude, Double referenceLongitude,
                            PolygonGeometry boundary, BoundarySource boundarySource,
                            BigDecimal declaredAreaM2, BigDecimal computedAreaM2, BigDecimal perimeterM,
                            AreaSource areaSource, BigDecimal areaM2, BigDecimal areaHa,
                            UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt, long version) {
    public static FieldResponse from(Field field, PolygonGeometry boundary) {
        return new FieldResponse(field.getId(), field.getFarmId(), field.getName(), field.getDescription(),
                field.getReferenceLatitude(), field.getReferenceLongitude(), boundary, field.getBoundarySource(),
                field.getDeclaredAreaM2(), field.getComputedAreaM2(), field.getPerimeterM(), field.getAreaSource(),
                field.getAreaM2(), field.getAreaM2().movePointLeft(4), field.getCreatedBy(), field.getUpdatedBy(),
                field.getCreatedAt(), field.getUpdatedAt(), field.getVersion());
    }
}
