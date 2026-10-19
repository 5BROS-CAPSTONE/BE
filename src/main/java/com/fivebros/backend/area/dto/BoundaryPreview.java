package com.fivebros.backend.area.dto;

import java.math.BigDecimal;

public record BoundaryPreview(PolygonGeometry boundary, int vertexCount,
                              BigDecimal computedAreaM2, BigDecimal areaHa, BigDecimal perimeterM) {
}
