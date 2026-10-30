package com.fivebros.backend.area;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.fivebros.backend.area.dto.BoundaryPreview;

@Entity
@Table(name = "fields")
public class Field extends AreaRecord {
    @Column(nullable = false, updatable = false)
    private UUID farmId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 2000)
    private String description;

    private Double referenceLatitude;
    private Double referenceLongitude;

    @Column(columnDefinition = "text")
    private String boundaryGeojson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BoundarySource boundarySource = BoundarySource.NONE;

    @Column(name = "declared_area_m2", precision = 24, scale = 6)
    private BigDecimal declaredAreaM2;

    @Column(name = "computed_area_m2", precision = 24, scale = 6)
    private BigDecimal computedAreaM2;

    @Column(name = "perimeter_m", precision = 24, scale = 6)
    private BigDecimal perimeterM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AreaSource areaSource = AreaSource.MANUAL;

    protected Field() {}

    Field(UUID farmId, UUID actor, Instant now) {
        super(actor, now);
        this.farmId = farmId;
    }

    void updateProfile(String name, String description, Double latitude, Double longitude, UUID actor, Instant now) {
        this.name = name.strip();
        this.description = description == null ? null : description.strip();
        referenceLatitude = latitude;
        referenceLongitude = longitude;
        touch(actor, now);
    }

    void declareArea(BigDecimal area) {
        declaredAreaM2 = area;
    }

    void replaceBoundary(String geojson, BoundarySource source, BoundaryPreview preview, UUID actor, Instant now) {
        boundaryGeojson = geojson;
        boundarySource = source;
        computedAreaM2 = preview.computedAreaM2();
        perimeterM = preview.perimeterM();
        areaSource = AreaSource.BOUNDARY;
        touch(actor, now);
    }

    public UUID getFarmId() {
        return farmId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Double getReferenceLatitude() {
        return referenceLatitude;
    }

    public Double getReferenceLongitude() {
        return referenceLongitude;
    }

    public String getBoundaryGeojson() {
        return boundaryGeojson;
    }

    public BoundarySource getBoundarySource() {
        return boundarySource;
    }

    public BigDecimal getDeclaredAreaM2() {
        return declaredAreaM2;
    }

    public BigDecimal getComputedAreaM2() {
        return computedAreaM2;
    }

    public BigDecimal getPerimeterM() {
        return perimeterM;
    }

    public AreaSource getAreaSource() {
        return areaSource;
    }

    public BigDecimal getAreaM2() {
        return areaSource == AreaSource.BOUNDARY ? computedAreaM2 : declaredAreaM2;
    }
}
