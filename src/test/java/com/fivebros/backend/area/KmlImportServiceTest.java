package com.fivebros.backend.area;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.fivebros.backend.common.error.ApiException;

class KmlImportServiceTest {
    private final AreaProperties properties = new AreaProperties(2000, 5242880, 100);
    private final KmlImportService service = new KmlImportService(new BoundaryService(properties), properties);
    static final String POLYGON =
            "<Polygon><outerBoundaryIs><LinearRing><coordinates>106.7,10.8,0 106.701,10.8,0 106.701,10.801,0 106.7,10.801,0</coordinates></LinearRing></outerBoundaryIs></Polygon>";

    @Test
    void returnsMultipleNamedCandidatesAndIgnoresAltitudeAndNetworkLinks() {
        var result = service.preview(file(
                "area.kml",
                wrap("<NetworkLink><Link><href>https://example.invalid/remote.kml</href></Link></NetworkLink>"
                        + "<Placemark><name>North &amp; South</name>" + POLYGON + "</Placemark>"
                        + "<Placemark><name>Second</name>" + POLYGON + "</Placemark>")));
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates().getFirst().placemarkName()).isEqualTo("North & South");
        assertThat(result.candidates())
                .allMatch(c -> c.valid() && c.preview().computedAreaM2().signum() > 0);
        assertThat(result.candidates().getFirst().warnings()).hasSize(1);
        assertThat(result.warnings()).hasSize(1);
    }

    @Test
    void preservesValidCandidatesAndReportsUnsupportedOrMalformedGeometryWithoutDroppingIt() {
        String holes = POLYGON.replace("</Polygon>", "<innerBoundaryIs><LinearRing/></innerBoundaryIs></Polygon>");
        String content = "<Placemark>" + POLYGON + "</Placemark>"
                + "<Placemark>" + holes + "</Placemark>"
                + "<Placemark><Point><coordinates>106,10</coordinates></Point></Placemark>"
                + "<Placemark><MultiGeometry>" + POLYGON + "</MultiGeometry></Placemark>"
                + "<Placemark>" + POLYGON.replace("106.7,10.8,0", "bad,10.8,0") + "</Placemark>"
                + "<Placemark><Polygon/></Placemark>"
                + "<Placemark><LineString><coordinates>106,10 107,11</coordinates></LineString></Placemark>";
        var candidates = service.preview(file("area.kml", wrap(content))).candidates();
        assertThat(candidates).hasSize(7);
        assertThat(candidates.getFirst().valid()).isTrue();
        assertThat(candidates.subList(1, 7))
                .allMatch(c -> !c.valid() && c.preview() == null && !c.errors().isEmpty());
        assertThat(candidates.get(1).errors().getFirst().code()).isEqualTo("UNSUPPORTED_KML_GEOMETRY");
        assertThat(candidates.get(4).errors().getFirst().code()).isEqualTo("INVALID_KML");
    }

    @Test
    void rejectsUnsafeXmlWrongNamespaceKmzAndOversizedFiles() {
        for (String xml : new String[] {
            "<kml>",
            "<kml xmlns='wrong'><Placemark/></kml>",
            "<!DOCTYPE kml [<!ENTITY xxe SYSTEM 'file:///must-not-be-read'>]>"
                    + wrap("<Placemark><name>&xxe;</name>" + POLYGON + "</Placemark>"),
            wrap("<Document/>"),
            wrap("<" + "Folder>".repeat(140))
        }) {
            assertThatThrownBy(() -> service.preview(file("area.kml", xml))).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> service.preview(file("area.kmz", wrap(""))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("KMZ");
        assertThatThrownBy(() -> service.preview(
                        new MockMultipartFile("file", "area.kml", "application/xml", new byte[5242881])))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void enforcesConfiguredVertexAndFeatureLimits() {
        var limited = new AreaProperties(3, 5242880, 1);
        var importer = new KmlImportService(new BoundaryService(limited), limited);
        assertThat(importer.preview(file("area.kml", wrap("<Placemark>" + POLYGON + "</Placemark>")))
                        .candidates()
                        .getFirst()
                        .valid())
                .isFalse();
        assertThatThrownBy(() -> importer.preview(file("area.kml", wrap("<Placemark/>".repeat(2)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Too many");
    }

    static String wrap(String content) {
        return "<kml xmlns='http://www.opengis.net/kml/2.2'><Document>" + content + "</Document></kml>";
    }

    static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile(
                "file", name, "application/vnd.google-earth.kml+xml", content.getBytes(StandardCharsets.UTF_8));
    }
}
