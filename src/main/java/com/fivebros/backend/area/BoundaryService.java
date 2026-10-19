package com.fivebros.backend.area;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import com.fivebros.backend.area.dto.BoundaryPreview;
import com.fivebros.backend.area.dto.PolygonGeometry;
import com.fivebros.backend.common.error.ApiException;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.PolygonArea;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BoundaryService {
    private final AreaProperties properties;
    private final GeometryFactory geometry = new GeometryFactory(new PrecisionModel(), 4326);

    public BoundaryService(AreaProperties properties) { this.properties = properties; }

    public BoundaryPreview preview(PolygonGeometry input) {
        if (input == null) throw invalid("A boundary is required");
        if (!"Polygon".equals(input.type())) {
            throw unsupported("Only a WGS84 Polygon is supported");
        }
        if (input.coordinates() == null || input.coordinates().isEmpty()) throw invalid("Exterior ring is missing");
        if (input.coordinates().size() != 1) throw unsupported("Polygon holes are not supported");
        List<List<Double>> ring = input.coordinates().getFirst();
        if (ring == null || ring.size() < 3) throw invalid("At least three distinct vertices are required");
        if (ring.size() > properties.maxVertices() + 1) throw invalid("Boundary exceeds the configured vertex limit");
        List<List<Double>> positions = new ArrayList<>();
        for (int i = 0; i < ring.size(); i++) {
            List<Double> position = ring.get(i);
            if (position == null || position.size() != 2) throw invalid("Vertex " + i + " must be [longitude, latitude]");
            validatePosition(position.get(1), position.get(0), "Vertex " + i);
            positions.add(List.of(zero(position.get(0)), zero(position.get(1))));
        }
        if (positions.getFirst().equals(positions.getLast())) positions.removeLast();
        if (positions.size() < 3 || positions.size() > properties.maxVertices()) {
            throw invalid("Boundary must have 3 to " + properties.maxVertices() + " vertices (excluding closure)");
        }
        if (new HashSet<>(positions).size() != positions.size()) throw invalid("Boundary contains repeated vertices or degenerate edges");
        double minLon = 180, maxLon = -180;
        for (int i = 0; i < positions.size(); i++) {
            var current = positions.get(i);
            var next = positions.get((i + 1) % positions.size());
            if (Math.abs(current.get(1)) == 90 || Math.abs(current.get(0) - next.get(0)) >= 180) {
                throw unsupported("Boundaries crossing the antimeridian or containing a pole are not supported");
            }
            minLon = Math.min(minLon, current.get(0));
            maxLon = Math.max(maxLon, current.get(0));
        }
        if (maxLon - minLon >= 180) throw unsupported("Boundary must describe a local land parcel");
        positions.add(positions.getFirst());
        Coordinate[] coordinates = positions.stream().map(p -> new Coordinate(p.get(0), p.get(1))).toArray(Coordinate[]::new);
        var polygon = geometry.createPolygon(coordinates);
        var error = new IsValidOp(polygon).getValidationError();
        if (error != null) throw invalid(error.getMessage() + " near " + error.getCoordinate());
        if (polygon.getArea() == 0) throw invalid("Boundary has zero area");
        if (!Orientation.isCCW(coordinates)) Collections.reverse(positions);

        PolygonArea geodesic = new PolygonArea(Geodesic.WGS84, false);
        for (int i = 0; i < positions.size() - 1; i++) {
            var position = positions.get(i);
            geodesic.AddPoint(position.get(1), position.get(0));
        }
        var result = geodesic.Compute(false, true);
        BigDecimal area = BigDecimal.valueOf(Math.abs(result.area)).setScale(6, RoundingMode.HALF_UP);
        BigDecimal perimeter = BigDecimal.valueOf(result.perimeter).setScale(6, RoundingMode.HALF_UP);
        if (area.signum() <= 0 || perimeter.signum() <= 0) throw invalid("Boundary is too small to measure");
        return new BoundaryPreview(new PolygonGeometry("Polygon", List.of(List.copyOf(positions))),
                positions.size() - 1, area, area.movePointLeft(4), perimeter);
    }

    static void validatePosition(Double latitude, Double longitude, String label) {
        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw invalid(label + " requires finite latitude [-90,90] and longitude [-180,180]");
        }
    }

    private double zero(double number) { return number == 0 ? 0.0 : number; }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOUNDARY", message);
    }

    private ApiException unsupported(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_GEOMETRY", message);
    }
}
