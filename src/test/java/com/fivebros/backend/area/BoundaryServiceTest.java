package com.fivebros.backend.area;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fivebros.backend.area.dto.PolygonGeometry;
import com.fivebros.backend.common.error.ApiException;

class BoundaryServiceTest {
    private final BoundaryService service = new BoundaryService(new AreaProperties(2000, 5242880, 100));

    @Test
    void matchesIndependentWgs84FixtureAndIncludesClosingEdge() {
        // Python GeographicLib WGS84 Polygon: (lat,lon) (0,0),(0,1),(1,1),(1,0).
        var ring = new ArrayList<>(List.of(p(0, 0), p(1, 0), p(1, 1), p(0, 1)));
        var result = service.preview(polygon(ring));
        assertThat(result.vertexCount()).isEqualTo(4);
        assertThat(result.boundary().coordinates().getFirst()).hasSize(5);
        assertThat(result.computedAreaM2().doubleValue()).isCloseTo(12308778361.469452, within(0.001));
        assertThat(result.perimeterM().doubleValue()).isCloseTo(443770.91724830196, within(0.00001));
        Collections.reverse(ring);
        var reversed = service.preview(polygon(ring));
        assertThat(reversed.computedAreaM2()).isEqualByComparingTo(result.computedAreaM2());
        assertThat(reversed.perimeterM()).isEqualByComparingTo(result.perimeterM());
        assertThat(service.preview(result.boundary())).isEqualTo(result);
    }

    @Test
    void rejectsInvalidAndUnsupportedBoundaries() {
        List<PolygonGeometry> invalid = List.of(
                polygon(List.of(p(0, 0), p(1, 1), p(1, 0), p(0, 1))),
                polygon(List.of(p(0, 0), p(1, 0))),
                polygon(List.of(p(0, 0), p(1, 0), p(2, 0))),
                polygon(List.of(p(0, 0), p(0, 0), p(1, 1))),
                polygon(List.of(p(0, 0), p(1, 0), p(1, 1), p(0, 0), p(0, 0))),
                polygon(List.of(p(181, 0), p(1, 0), p(1, 1))),
                polygon(List.of(p(0, 91), p(1, 0), p(1, 1))),
                polygon(List.of(p(Double.NaN, 0), p(1, 0), p(1, 1))),
                polygon(List.of(p(0, Double.POSITIVE_INFINITY), p(1, 0), p(1, 1))),
                polygon(List.of(List.of(0.0, 0.0, 0.0), p(1, 0), p(1, 1))),
                polygon(List.of(p(179, 0), p(-179, 0), p(-179, 1))),
                polygon(List.of(p(0, 90), p(1, 89), p(-1, 89))),
                new PolygonGeometry("MultiPolygon", List.of()),
                new PolygonGeometry("Polygon", List.of(List.of(p(0, 0), p(1, 0), p(1, 1)), List.of(p(0, 0)))));
        for (var input : invalid)
            assertThatThrownBy(() -> service.preview(input)).isInstanceOf(ApiException.class);
        BoundaryService limited = new BoundaryService(new AreaProperties(3, 1000, 2));
        assertThatThrownBy(() -> limited.preview(polygon(List.of(p(0, 0), p(1, 0), p(1, 1), p(0, 1)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("3 to 3");
    }

    static PolygonGeometry polygon(List<List<Double>> ring) {
        return new PolygonGeometry("Polygon", List.of(ring));
    }

    static List<Double> p(double lon, double lat) {
        return List.of(lon, lat);
    }
}
