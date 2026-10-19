package com.fivebros.backend.fleet;

import java.util.*;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FleetIntegrationTest extends ApiIntegrationTest {
    @Autowired UavProfileRepository profiles;
    @Autowired DroneRepository drones;

    @AfterEach
    void cleanFleet() {
        drones.deleteAllInBatch();
        profiles.deleteAll(); // Delete element collections before profiles.
    }

    @Test
    void expertCreatesSharedProfileAndTechnicianRegistersAndUpdatesPhysicalDrones() throws Exception {
        UserAccount expert = actor(Role.EXPERT);
        String expertToken = login(expert);
        JsonNode profile = createProfile(expertToken, profile("Agras T25"));
        String profileId = profile.get("id").asString();
        assertThat(profile.get("createdBy").asString()).isEqualTo(expert.getId().toString());
        assertThat(profile.get("manufacturer").asString()).isEqualTo("DJI");
        assertThat(profile.get("specifications").get("batteryCapacityAh").asDouble()).isEqualTo(15.5);
        assertThat(profile.get("specifications").get("batteryEnergyWh").asDouble()).isEqualTo(809);
        assertThat(profile.get("capabilities").get(0).asString()).isEqualTo("SPRAYING");
        assertThat(profile.get("dataStatus").asString()).isEqualTo("UNVERIFIED");

        UserAccount technician = actor(Role.TECHNICIAN);
        String techToken = login(technician);
        JsonNode first = createDrone(techToken, " T25-001 ", " Drone 1 ", profileId);
        JsonNode second = createDrone(techToken, "T25-002", "Drone 2", profileId);
        String firstId = first.get("id").asString();
        assertThat(first.get("serial").asString()).isEqualTo("T25-001");
        assertThat(first.get("name").asString()).isEqualTo("Drone 1");
        assertThat(first.get("createdBy").asString()).isEqualTo(technician.getId().toString());
        assertThat(second.get("uavProfile").get("id").asString()).isEqualTo(profileId);
        assertThat(first.get("batteryInstallation").get("integrated").asBoolean()).isFalse();
        assertThat(first.get("batteryInstallation").get("batteryIds").isNull()).isTrue();

        Map<String, Object> updatedProfile = profile("Agras T25 revised");
        updatedProfile.put("version", 0);
        putBody(expertToken, "/uav-profiles/" + profileId, updatedProfile)
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        getBody(techToken, "/drones/" + firstId).andExpect(status().isOk())
                .andExpect(jsonPath("$.uavProfile.modelName").value("Agras T25 revised"));
        getBody(techToken, "/drones/" + second.get("id").asString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.uavProfile.modelName").value("Agras T25 revised"));

        JsonNode newProfile = createProfile(expertToken, profile("Agras T50"));
        putBody(techToken, "/drones/" + firstId, Map.of("serial", "T25-001", "name", "Renamed",
                        "uavProfileId", newProfile.get("id").asString(), "version", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.uavProfile.modelName").value("Agras T50"));
        assertThat(profiles.count()).isEqualTo(2);
        assertThat(drones.count()).isEqualTo(2);
    }

    @Test
    void missingDatasheetValuesStayNullAndOptionalMetadataCanBeCleared() throws Exception {
        String token = login(admin);
        Map<String, Object> payload = profile("Agras T55");
        payload.put("specifications", Map.of("batteryCount", 1, "maxTakeoffWeightKg", 95, "maxPayloadKg", 50,
                "minOperatingTemperatureC", -10, "maxOperatingTemperatureC", 40));
        payload.put("dataStatus", "DATASHEET_INCOMPLETE");
        payload.put("notes", "Khối lượng rỗng và thời gian bay: datasheet không có");
        JsonNode profile = createProfile(token, payload);
        String id = profile.get("id").asString();
        getBody(token, "/uav-profiles/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.specifications.emptyWeightKg").isEmpty())
                .andExpect(jsonPath("$.specifications.batteryEnergyWh").isEmpty())
                .andExpect(jsonPath("$.specifications.maxFlightTimeMinutes").isEmpty())
                .andExpect(jsonPath("$.specifications.minOperatingTemperatureC").value(-10))
                .andExpect(jsonPath("$.dataStatus").value("DATASHEET_INCOMPLETE"));
        payload.put("version", 0);
        payload.remove("sourceUrl");
        payload.remove("notes");
        putBody(token, "/uav-profiles/" + id, payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceUrl").isEmpty()).andExpect(jsonPath("$.notes").isEmpty());
    }

    @Test
    void validatesUnitsRangesCapabilitiesAndSourceWithoutCreatingInvalidProfiles() throws Exception {
        String token = login(admin);
        List<Map<String, Object>> invalidSpecs = List.of(
                Map.of("batteryCount", 0), Map.of("batteryCount", 1, "batteryCapacityAh", -1),
                Map.of("batteryCount", 1, "batteryEnergyWh", 0), Map.of("batteryCount", 1, "maxSpeedMps", 0),
                Map.of("batteryCount", 1, "maxFlightTimeMinutes", -2),
                Map.of("batteryCount", 1, "maxWindSpeedMps", -1),
                Map.of("batteryCount", 1, "minOperatingTemperatureC", 40, "maxOperatingTemperatureC", 0),
                Map.of("batteryCount", 1, "minOperatingTemperatureC", -274),
                Map.of("batteryCount", 1, "emptyWeightKg", 100, "maxTakeoffWeightKg", 50),
                Map.of("batteryCount", 1, "maxPayloadKg", 60, "maxTakeoffWeightKg", 50),
                Map.of("batteryCount", 1, "batteryEnergyWh", 1.1234567),
                Map.of("batteryCount", 1, "batteryCapacityAh", "NaN"),
                Map.of("batteryCount", 1, "emptyWeightKg", "1000000000000"),
                Map.of("maxPayloadKg", 20));
        for (Map<String, Object> specs : invalidSpecs) {
            Map<String, Object> request = profile("Invalid");
            request.put("specifications", specs);
            postBody(token, "/uav-profiles", request).andExpect(status().isBadRequest());
        }
        for (Map.Entry<String, Object> invalid : List.<Map.Entry<String, Object>>of(
                Map.entry("manufacturer", " "), Map.entry("modelName", " "),
                Map.entry("sourceUrl", "javascript:alert(1)"), Map.entry("sourceUrl", "https://"),
                Map.entry("capabilities", List.of("spraying", "SPRAYING")),
                Map.entry("capabilities", List.of(" ")), Map.entry("dataStatus", "UNKNOWN"))) {
            Map<String, Object> request = profile("Invalid");
            request.put(invalid.getKey(), invalid.getValue());
            postBody(token, "/uav-profiles", request).andExpect(status().isBadRequest());
        }
        assertThat(profiles.count()).isZero();
    }

    @Test
    void duplicateProfileIdentityAndSerialAreRejectedIgnoringCaseAndOuterWhitespace() throws Exception {
        String token = login(admin);
        String profileId = createProfile(token, profile("Agras T25")).get("id").asString();
        Map<String, Object> duplicate = profile(" agras t25 ");
        duplicate.put("manufacturer", "dji");
        postBody(token, "/uav-profiles", duplicate).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UAV_PROFILE_EXISTS"));
        String droneId = createDrone(token, "T25-001", "First", profileId).get("id").asString();
        postBody(token, "/drones", Map.of("serial", " t25-001 ", "name", "Duplicate", "uavProfileId", profileId))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRONE_SERIAL_EXISTS"));
        String otherId = createDrone(token, "T25-002", "Second", profileId).get("id").asString();
        putBody(token, "/drones/" + otherId, Map.of("serial", "t25-001", "name", "Duplicate", "uavProfileId", profileId, "version", 0))
                .andExpect(status().isConflict());
        getBody(token, "/drones/" + droneId).andExpect(jsonPath("$.name").value("First"));
        getBody(token, "/drones/" + otherId).andExpect(jsonPath("$.serial").value("T25-002"));
    }

    @Test
    void listSupportsCombinedFiltersAndPaginationAndTreatsSearchWildcardsLiterally() throws Exception {
        String token = login(admin);
        String p1 = createProfile(token, profile("Agras T25")).get("id").asString();
        String p2 = createProfile(token, profile("Agras T50")).get("id").asString();
        createDrone(token, "A-1", "Field Alpha", p1);
        String unavailableId = createDrone(token, "A-2", "Field Beta", p1).get("id").asString();
        createDrone(token, "B-1", "Field Gamma", p2);
        // Stand-in for FLT-06; status is intentionally not writable through FLT-02.
        Drone unavailable = drones.findById(UUID.fromString(unavailableId)).orElseThrow();
        unavailable.status = DroneStatus.UNAVAILABLE;
        drones.saveAndFlush(unavailable);
        mvc.perform(get("/api/v1/drones").header("Authorization", bearer(token))
                        .param("search", "FIELD").param("uavProfileId", p1).param("status", "AVAILABLE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].serial").value("A-1"));
        mvc.perform(get("/api/v1/drones").header("Authorization", bearer(token)).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/v1/uav-profiles").header("Authorization", bearer(token)).param("search", "dji"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/drones").header("Authorization", bearer(token)).param("search", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/drones").header("Authorization", bearer(token)).param("status", "UNAVAILABLE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(unavailableId));
    }

    @Test
    void staleVersionsDoNotOverwriteProfileOrDroneAndMissingVersionsAreRejected() throws Exception {
        String token = login(admin);
        String profileId = createProfile(token, profile("Agras T25")).get("id").asString();
        Map<String, Object> updated = profile("New name");
        updated.put("version", 0);
        putBody(token, "/uav-profiles/" + profileId, updated).andExpect(status().isOk());
        putBody(token, "/uav-profiles/" + profileId, updated).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        putBody(token, "/uav-profiles/" + profileId, profile("Missing version")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERSION_REQUIRED"));
        String droneId = createDrone(token, "T25-001", "Initial", profileId).get("id").asString();
        Map<String, Object> drone = Map.of("serial", "T25-001", "name", "New drone name", "uavProfileId", profileId, "version", 0);
        putBody(token, "/drones/" + droneId, drone).andExpect(status().isOk());
        putBody(token, "/drones/" + droneId, drone).andExpect(status().isConflict());
        putBody(token, "/drones/" + droneId, Map.of("serial", "T25-001", "name", "Missing", "uavProfileId", profileId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VERSION_REQUIRED"));
        getBody(token, "/drones/" + droneId).andExpect(jsonPath("$.name").value("New drone name"));
    }

    @Test
    void rejectsMissingReferencesInvalidFiltersAndLifecycleEdits() throws Exception {
        String token = login(admin);
        String missing = UUID.randomUUID().toString();
        postBody(token, "/drones", Map.of("serial", "X", "name", "X", "uavProfileId", missing))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("UAV_PROFILE_NOT_FOUND"));
        getBody(token, "/drones/" + missing).andExpect(status().isNotFound());
        getBody(token, "/uav-profiles/" + missing).andExpect(status().isNotFound());
        for (String path : List.of("/drones?size=101", "/drones?page=-1", "/drones?status=INVALID",
                "/drones?uavProfileId=bad", "/uav-profiles?size=0", "/drones/bad")) {
            getBody(token, path).andExpect(status().isBadRequest());
        }
        String profileId = createProfile(token, profile("Agras T25")).get("id").asString();
        String droneId = createDrone(token, "T25-001", "Initial", profileId).get("id").asString();
        for (DroneStatus lifecycle : List.of(DroneStatus.IN_MISSION, DroneStatus.RETIRED)) {
            Drone drone = drones.findById(UUID.fromString(droneId)).orElseThrow();
            drone.status = lifecycle;
            Drone saved = drones.saveAndFlush(drone);
            putBody(token, "/drones/" + droneId, Map.of("serial", "T25-001", "name", "Changed",
                            "uavProfileId", profileId, "version", saved.version))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRONE_NOT_EDITABLE"));
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void enforcesRolePermissionsForEveryFleetEndpoint(Role role) throws Exception {
        String adminToken = login(admin);
        JsonNode profile = createProfile(adminToken, profile("Agras T25"));
        String profileId = profile.get("id").asString();
        String droneId = createDrone(adminToken, "Initial", "Initial", profileId).get("id").asString();
        String token = role == Role.ADMIN ? adminToken : role == Role.OPERATOR ? login(operator) : login(actor(role));
        boolean profileRead = Set.of(Role.ADMIN, Role.EXPERT, Role.MANAGER, Role.TECHNICIAN).contains(role);
        boolean profileWrite = Set.of(Role.ADMIN, Role.EXPERT).contains(role);
        boolean droneRead = Set.of(Role.ADMIN, Role.MANAGER, Role.TECHNICIAN).contains(role);
        boolean droneWrite = Set.of(Role.ADMIN, Role.TECHNICIAN).contains(role);
        for (String path : List.of("/uav-profiles", "/uav-profiles/" + profileId)) {
            getBody(token, path).andExpect(profileRead ? status().isOk() : status().isForbidden());
        }
        for (String path : List.of("/drones", "/drones/" + droneId)) {
            getBody(token, path).andExpect(droneRead ? status().isOk() : status().isForbidden());
        }
        postBody(token, "/uav-profiles", profile("Another"))
                .andExpect(profileWrite ? status().isCreated() : status().isForbidden());
        Map<String, Object> update = profile("Updated");
        update.put("version", 0);
        putBody(token, "/uav-profiles/" + profileId, update)
                .andExpect(profileWrite ? status().isOk() : status().isForbidden());
        postBody(token, "/drones", Map.of("serial", "Another", "name", "Another", "uavProfileId", profileId))
                .andExpect(droneWrite ? status().isCreated() : status().isForbidden());
        putBody(token, "/drones/" + droneId, Map.of("serial", "Initial", "name", "Updated", "uavProfileId", profileId, "version", 0))
                .andExpect(droneWrite ? status().isOk() : status().isForbidden());
    }

    @Test
    void fleetEndpointsRequireAuthenticationAndAreDocumentedInOpenApi() throws Exception {
        mvc.perform(get("/api/v1/drones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/uav-profiles")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/drones").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/uav-profiles'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/drones'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/drones/{id}'].put").exists())
                .andExpect(jsonPath("$.components.schemas.ProfileSpecifications.properties.batteryEnergyWh").exists())
                .andExpect(jsonPath("$.components.schemas.Jwt").doesNotExist());
    }

    private UserAccount actor(Role role) {
        return users.saveAndFlush(new UserAccount(role.name().toLowerCase() + "@fleet.test", role.label(),
                admin.getPasswordHash(), Set.of(role), clock.instant()));
    }

    private Map<String, Object> profile(String model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("manufacturer", " DJI ");
        payload.put("modelName", model);
        payload.put("specifications", Map.ofEntries(
                Map.entry("emptyWeightKg", 25.4), Map.entry("maxTakeoffWeightKg", 52), Map.entry("maxPayloadKg", 20),
                Map.entry("batteryCapacityAh", 15.5), Map.entry("batteryEnergyWh", 809), Map.entry("batteryCount", 1),
                Map.entry("maxWindSpeedMps", 6), Map.entry("minOperatingTemperatureC", 0),
                Map.entry("maxOperatingTemperatureC", 45), Map.entry("maxSpeedMps", 10), Map.entry("maxFlightTimeMinutes", 14.5)));
        payload.put("capabilities", List.of("spraying"));
        payload.put("sourceUrl", "https://ag.dji.com/t25/downloads");
        payload.put("notes", "MTOW/tải khi phun; thời gian bay treo không tải (32 kg), gió < 3 m/s, 25 °C.");
        return payload;
    }

    private JsonNode createProfile(String token, Map<String, Object> payload) throws Exception {
        return read(postBody(token, "/uav-profiles", payload).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString());
    }

    private JsonNode createDrone(String token, String serial, String name, String profileId) throws Exception {
        return read(postBody(token, "/drones", Map.of("serial", serial, "name", name, "uavProfileId", profileId))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("AVAILABLE")).andReturn().getResponse().getContentAsString());
    }

    private ResultActions getBody(String token, String path) throws Exception {
        return mvc.perform(get("/api/v1" + path).header("Authorization", bearer(token)));
    }

    private ResultActions postBody(String token, String path, Object payload) throws Exception {
        return mvc.perform(post("/api/v1" + path).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body(payload)));
    }

    private ResultActions putBody(String token, String path, Object payload) throws Exception {
        return mvc.perform(put("/api/v1" + path).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body(payload)));
    }
}
