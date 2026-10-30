package com.fivebros.backend.area.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "WGS84 GeoJSON Polygon, one exterior ring only; positions are [longitude, latitude]. "
                + "An unclosed ring is closed by the server. Holes and MultiPolygon are unsupported.")
public record PolygonGeometry(
        @Schema(example = "Polygon", allowableValues = "Polygon")
        String type,

        @Schema(example = "[[[106.7,10.8],[106.701,10.8],[106.701,10.801],[106.7,10.801],[106.7,10.8]]]")
        List<List<List<Double>>> coordinates) {}
