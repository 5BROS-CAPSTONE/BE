package com.fivebros.backend.area;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fivebros.backend.account.UserAccount;
import com.fivebros.backend.security.Role;
import com.fivebros.backend.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AreaIntegrationTest extends ApiIntegrationTest {
    @Autowired FarmRepository farms;
    @Autowired FieldRepository fields;

    @AfterEach
    void cleanAreas() {
        fields.deleteAllInBatch();
        farms.deleteAllInBatch();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "MANAGER"})
    void authorizedRolesCanCreateReadSearchAndUpdateFarmAndManualFields(Role role) throws Exception {
        UserAccount actor = role == Role.ADMIN ? admin : users.saveAndFlush(new UserAccount("manager@example.test", "Manager",
                admin.getPasswordHash(), Set.of(role), clock.instant()));
        String token = login(actor);
        JsonNode farm = createFarm(token, "  Trang trại A  ");
        String farmId = farm.get("id").asString();
        assertThat(farm.get("createdBy").asString()).isEqualTo(actor.getId().toString());
        assertThat(farm.get("name").asString()).isEqualTo("Trang trại A");
        mvc.perform(get("/api/v1/farms/" + farmId).header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/farms").param("search", "TRẠI").param("size", "1").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(farmId));
        sendPut(token, "/farms/" + farmId, Map.of("name", "Updated", "version", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        String field = sendPost(token, "/farms/" + farmId + "/fields", Map.of("name", "  Lô 1  ", "declaredArea", 1.5, "areaUnit", "HA"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.areaM2").value(15000)).andExpect(jsonPath("$.areaHa").value(1.5))
                .andExpect(jsonPath("$.areaSource").value("MANUAL")).andExpect(jsonPath("$.boundarySource").value("NONE"))
                .andExpect(jsonPath("$.boundary").isEmpty()).andExpect(jsonPath("$.perimeterM").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String fieldId = read(field).get("id").asString();
        mvc.perform(get("/api/v1/fields/" + fieldId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.declaredAreaM2").value(15000));
        sendPut(token, "/fields/" + fieldId, Map.of("name", "Lô mới", "version", 0, "declaredArea", 20000,
                "referenceLatitude", 10.8, "referenceLongitude", 106.7))
                .andExpect(status().isOk()).andExpect(jsonPath("$.areaM2").value(20000)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/farms/" + farmId + "/fields").param("search", "MỚI").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        String anotherFarm = createFarm(token, "Other").get("id").asString();
        mvc.perform(get("/api/v1/farms/" + anotherFarm + "/fields").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"EXPERT", "OPERATOR", "TECHNICIAN", "ANALYST"})
    void otherRolesCannotReadWriteOrPreviewAreas(Role role) throws Exception {
        UserAccount actor = users.saveAndFlush(new UserAccount(role.name().toLowerCase() + "@area.test", role.label(),
                admin.getPasswordHash(), Set.of(role), clock.instant()));
        String token = login(actor);
        String id = UUID.randomUUID().toString();
        for (String path : List.of("/farms", "/farms/" + id, "/farms/" + id + "/fields", "/fields/" + id)) {
            mvc.perform(get("/api/v1" + path).header("Authorization", bearer(token))).andExpect(status().isForbidden());
        }
        sendPost(token, "/farms", Map.of("name", "Denied")).andExpect(status().isForbidden());
        sendPost(token, "/farms/" + id + "/fields", Map.of("name", "Denied", "declaredArea", 1)).andExpect(status().isForbidden());
        sendPost(token, "/fields/boundary/preview", Map.of("boundary", polygon())).andExpect(status().isForbidden());
        sendPut(token, "/farms/" + id, Map.of("name", "Denied", "version", 0)).andExpect(status().isForbidden());
        sendPut(token, "/fields/" + id, Map.of("name", "Denied", "version", 0)).andExpect(status().isForbidden());
        sendPut(token, "/fields/" + id + "/boundary", Map.of("boundary", polygon(), "version", 0)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/fields/import-kml/preview").file(KmlImportServiceTest.file("a.kml", "bad"))
                .header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/farms")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/farms").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/fields/import-kml/preview").file(KmlImportServiceTest.file("a.kml", "bad")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void boundaryAndKmlPreviewsAreReadOnlyAndSavingRecalculatesMeasurements() throws Exception {
        String token = login(admin);
        sendPost(token, "/fields/boundary/preview", Map.of("boundary", polygon(), "computedAreaM2", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vertexCount").value(4))
                .andExpect(jsonPath("$.computedAreaM2").value(greaterThan(10000.0)));
        String result = mvc.perform(multipart("/api/v1/fields/import-kml/preview")
                        .file(KmlImportServiceTest.file("area.kml", KmlImportServiceTest.wrap("<Placemark><name>Rice</name>" + KmlImportServiceTest.POLYGON + "</Placemark>")))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.candidates[0].valid").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(fields.count()).isZero();
        assertThat(farms.count()).isZero();
        String farmId = createFarm(token, "Farm").get("id").asString();
        Object selected = read(result).get("candidates").get(0).get("preview").get("boundary");
        String saved = sendPost(token, "/farms/" + farmId + "/fields", Map.of("name", "Rice", "boundary", selected,
                        "boundarySource", "KML", "declaredArea", 1, "computedAreaM2", 1, "perimeterM", 1))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.boundarySource").value("KML"))
                .andExpect(jsonPath("$.areaSource").value("BOUNDARY")).andExpect(jsonPath("$.declaredAreaM2").value(1))
                .andExpect(jsonPath("$.areaM2").value(greaterThan(10000.0)))
                .andReturn().getResponse().getContentAsString();
        String fieldId = read(saved).get("id").asString();
        sendPut(token, "/fields/" + fieldId, Map.of("name", "Rice renamed", "version", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.boundarySource").value("KML"));
        sendPut(token, "/fields/" + fieldId, Map.of("name", "Rice", "version", 1, "declaredArea", 12))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BOUNDARY_AREA_READ_ONLY"));
        sendPut(token, "/fields/" + fieldId + "/boundary", Map.of("version", 1, "boundary", invalidPolygon()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BOUNDARY"));
        mvc.perform(get("/api/v1/fields/" + fieldId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.boundarySource").value("KML"));
        sendPut(token, "/fields/" + fieldId + "/boundary", Map.of("version", 1, "boundary", polygon()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.boundarySource").value("MANUAL_COORDINATES"));
        sendPut(token, "/fields/" + fieldId + "/boundary", Map.of("version", 1, "boundary", polygon()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void validatesAreaReferencePaginationAndMissingResources() throws Exception {
        String token = login(admin);
        String farmId = createFarm(token, "Farm").get("id").asString();
        for (Map<String, Object> payload : List.<Map<String, Object>>of(
                Map.of("name", "No area"), Map.of("name", "Zero", "declaredArea", 0),
                Map.of("name", "Negative", "declaredArea", -1), Map.of("name", "Unit only", "areaUnit", "HA"),
                Map.of("name", "Reference only", "referenceLatitude", 10, "declaredArea", 1),
                Map.of("name", "Bad reference", "referenceLatitude", 91, "referenceLongitude", 10, "declaredArea", 1),
                Map.of("name", "No geometry", "boundarySource", "KML", "declaredArea", 1),
                Map.of("name", " ", "declaredArea", 1),
                Map.of("name", "Bad unit", "declaredArea", 1, "areaUnit", "ACRE"))) {
            sendPost(token, "/farms/" + farmId + "/fields", payload).andExpect(status().isBadRequest());
        }
        assertThat(fields.count()).isZero();
        mvc.perform(get("/api/v1/farms").param("size", "101").header("Authorization", bearer(token))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/farms").param("page", "-1").header("Authorization", bearer(token))).andExpect(status().isBadRequest());
        String missing = UUID.randomUUID().toString();
        sendPost(token, "/farms/" + missing + "/fields", Map.of("name", "Missing", "declaredArea", 1))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"));
        mvc.perform(get("/api/v1/fields/" + missing).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FIELD_NOT_FOUND"));
        sendPut(token, "/farms/" + farmId, Map.of("name", "Version missing"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VERSION_REQUIRED"));
    }

    @Test
    void manualFieldCanAcquireBoundaryAndStaleEditsNeverOverwriteNewData() throws Exception {
        String token = login(admin);
        String farmId = createFarm(token, "Farm").get("id").asString();
        String fieldId = read(sendPost(token, "/farms/" + farmId + "/fields", Map.of("name", "Plot", "declaredArea", 100))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        sendPut(token, "/fields/" + fieldId + "/boundary", Map.of("boundary", polygon(), "version", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.areaSource").value("BOUNDARY"))
                .andExpect(jsonPath("$.declaredAreaM2").value(100));
        sendPut(token, "/fields/" + fieldId, Map.of("name", "Stale", "version", 0))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        sendPut(token, "/farms/" + farmId, Map.of("name", "New", "version", 0)).andExpect(status().isOk());
        sendPut(token, "/farms/" + farmId, Map.of("name", "Stale", "version", 0))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(get("/api/v1/farms/" + farmId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New"));
    }

    @Test
    void simultaneousFarmUpdatesAllowOnlyOneWriter() throws Exception {
        String token = login(admin);
        String farmId = createFarm(token, "Farm").get("id").asString();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return sendPut(token, "/farms/" + farmId, Map.of("name", "Writer A", "version", 0)).andReturn().getResponse().getStatus();
            });
            var b = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return sendPut(token, "/farms/" + farmId, Map.of("name", "Writer B", "version", 0)).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        } finally { start.countDown(); }
    }

    @Test
    void swaggerExposesAreaContractsAndHidesJwtPrincipal() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/farms'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/farms/{farmId}/fields'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/fields/import-kml/preview'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.components.schemas.PolygonGeometry.properties.coordinates").exists())
                .andExpect(jsonPath("$.components.schemas.Jwt").doesNotExist());
    }

    private JsonNode createFarm(String token, String name) throws Exception {
        return read(sendPost(token, "/farms", Map.of("name", name)).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString());
    }
    private ResultActions sendPost(String token, String path, Object payload) throws Exception {
        return mvc.perform(post("/api/v1" + path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body(payload)));
    }
    private ResultActions sendPut(String token, String path, Object payload) throws Exception {
        return mvc.perform(put("/api/v1" + path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body(payload)));
    }
    private Map<String, Object> polygon() {
        return Map.of("type", "Polygon", "coordinates", List.of(List.of(List.of(106.7, 10.8), List.of(106.701, 10.8),
                List.of(106.701, 10.801), List.of(106.7, 10.801))));
    }
    private Map<String, Object> invalidPolygon() {
        return Map.of("type", "Polygon", "coordinates", List.of(List.of(List.of(0, 0), List.of(1, 1), List.of(1, 0), List.of(0, 1))));
    }
    private org.hamcrest.Matcher<Double> greaterThan(double number) { return org.hamcrest.Matchers.greaterThan(number); }
}
