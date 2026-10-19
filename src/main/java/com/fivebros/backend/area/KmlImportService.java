package com.fivebros.backend.area;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import com.fivebros.backend.area.dto.KmlPreviewResponse;
import com.fivebros.backend.area.dto.KmlPreviewResponse.Candidate;
import com.fivebros.backend.area.dto.KmlPreviewResponse.Issue;
import com.fivebros.backend.area.dto.PolygonGeometry;
import com.fivebros.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

@Service
public class KmlImportService {
    private static final String KML = "http://www.opengis.net/kml/2.2";
    private static final Set<String> GEOMETRIES = Set.of("Polygon", "Point", "LineString", "LinearRing", "MultiGeometry", "Model", "Track", "MultiTrack");
    private final BoundaryService boundaries;
    private final AreaProperties properties;

    public KmlImportService(BoundaryService boundaries, AreaProperties properties) {
        this.boundaries = boundaries;
        this.properties = properties;
    }

    public KmlPreviewResponse preview(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".kmz")) {
            throw error("UNSUPPORTED_KML_GEOMETRY", "KMZ is not supported; upload an uncompressed .kml file");
        }
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".kml")) {
            throw error("INVALID_KML", "Upload a .kml file");
        }
        if (file.isEmpty()) throw error("INVALID_KML", "KML file is empty");
        if (file.getSize() > properties.maxKmlBytes()) throw tooLarge();
        byte[] bytes;
        try (var stream = file.getInputStream()) {
            bytes = stream.readNBytes(properties.maxKmlBytes() + 1);
        } catch (IOException exception) {
            throw error("INVALID_KML", "KML file could not be read");
        }
        if (bytes.length > properties.maxKmlBytes()) throw tooLarge();
        Document document = parse(bytes);
        Element root = document.getDocumentElement();
        if (!KML.equals(root.getNamespaceURI()) || !"kml".equals(root.getLocalName())) {
            throw error("INVALID_KML", "Expected a kml root in the KML 2.2 namespace");
        }
        var placemarks = root.getElementsByTagNameNS(KML, "Placemark");
        if (placemarks.getLength() == 0) throw error("INVALID_KML", "KML contains no Placemark features");
        if (placemarks.getLength() > properties.maxKmlFeatures()) throw error("INVALID_KML", "Too many KML features");
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < placemarks.getLength(); i++) {
            Element placemark = (Element) placemarks.item(i);
            List<Element> names = children(placemark, "name");
            String name = names.isEmpty() ? null : names.getFirst().getTextContent().strip();
            int before = candidates.size();
            for (Node child = placemark.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof Element element) || !GEOMETRIES.contains(element.getLocalName())) continue;
                if (candidates.size() >= properties.maxKmlFeatures()) throw error("INVALID_KML", "Too many KML geometries");
                candidates.add(candidate(candidates.size(), name, element));
            }
            if (candidates.size() == before) {
                if (candidates.size() >= properties.maxKmlFeatures()) throw error("INVALID_KML", "Too many KML geometries");
                candidates.add(failed(candidates.size(), name, "UNSUPPORTED_KML_GEOMETRY", "Placemark has no supported polygon geometry"));
            }
        }
        List<String> warnings = root.getElementsByTagNameNS(KML, "NetworkLink").getLength() > 0
                ? List.of("NetworkLink resources were ignored and were not fetched") : List.of();
        return new KmlPreviewResponse(List.copyOf(candidates), warnings);
    }

    private Candidate candidate(int index, String name, Element polygon) {
        try {
            if (!KML.equals(polygon.getNamespaceURI()) || !"Polygon".equals(polygon.getLocalName())) {
                throw error("UNSUPPORTED_KML_GEOMETRY", polygon.getLocalName() + " is not supported; supply a single Polygon");
            }
            if (polygon.getElementsByTagNameNS(KML, "innerBoundaryIs").getLength() > 0) {
                throw error("UNSUPPORTED_KML_GEOMETRY", "Polygon holes are not supported");
            }
            Element outer = single(polygon, "outerBoundaryIs");
            Element ring = single(outer, "LinearRing");
            String text = single(ring, "coordinates").getTextContent().strip();
            if (text.isEmpty()) throw error("INVALID_KML", "Polygon coordinates are empty");
            List<List<Double>> positions = new ArrayList<>();
            boolean altitude = false;
            for (String tuple : text.split("\\s+")) {
                if (positions.size() >= properties.maxVertices() + 1) throw error("INVALID_BOUNDARY", "Polygon exceeds the configured vertex limit");
                String[] values = tuple.split(",", -1);
                if (values.length < 2 || values.length > 3) throw error("INVALID_KML", "Expected longitude,latitude[,altitude]");
                double longitude = finite(values[0]);
                double latitude = finite(values[1]);
                if (values.length == 3) {
                    finite(values[2]);
                    altitude = true;
                }
                positions.add(List.of(longitude, latitude));
            }
            var preview = boundaries.preview(new PolygonGeometry("Polygon", List.of(positions)));
            return new Candidate(index, name, true, preview, List.of(),
                    altitude ? List.of("Altitude was ignored; measurements use the 2D WGS84 boundary") : List.of());
        } catch (ApiException exception) {
            return failed(index, name, exception.code(), exception.getMessage());
        }
    }

    private double finite(String text) {
        try {
            double number = Double.parseDouble(text);
            if (!Double.isFinite(number)) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) {
            throw error("INVALID_KML", "Coordinates must be finite numbers");
        }
    }

    private Document parse(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "128");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External XML resources are disabled"); });
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException exception) throws SAXException { throw exception; }
                @Override public void fatalError(SAXParseException exception) throws SAXException { throw exception; }
            });
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (SAXException | IOException exception) {
            throw error("INVALID_KML", "Malformed or unsafe KML XML; DTD and external resources are disabled");
        } catch (javax.xml.parsers.ParserConfigurationException exception) {
            throw new IllegalStateException("Secure KML XML parser is unavailable", exception);
        }
    }

    private Element single(Element parent, String name) {
        List<Element> elements = children(parent, name);
        if (elements.size() != 1) throw error("INVALID_KML", "Polygon requires exactly one " + name);
        return elements.getFirst();
    }

    private List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && KML.equals(element.getNamespaceURI()) && name.equals(element.getLocalName())) result.add(element);
        }
        return result;
    }

    private Candidate failed(int index, String name, String code, String message) {
        return new Candidate(index, name, false, null, List.of(new Issue(code, message)), List.of());
    }

    private ApiException error(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }

    private ApiException tooLarge() { return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "KML_TOO_LARGE", "KML file exceeds the configured size limit"); }
}
