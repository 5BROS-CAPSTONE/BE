package com.fivebros.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;

import com.fivebros.backend.security.Role;
import com.fivebros.backend.support.ApiIntegrationTest;

class UserAccountIntegrationTest extends ApiIntegrationTest {

    @Test
    void adminCanCreateReadSearchAndUpdateAnAccount() throws Exception {
        String token = login(admin);
        String response = mvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "email",
                                "MANAGER@EXAMPLE.TEST",
                                "fullName",
                                "Mission Manager",
                                "password",
                                PASSWORD,
                                "roles",
                                Set.of("MANAGER")))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value("manager@example.test"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = read(response).get("id").asString();
        UserAccount saved = users.findById(UUID.fromString(id)).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, saved.getPasswordHash())).isTrue();

        mvc.perform(get("/api/v1/users/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Mission Manager"));
        mvc.perform(get("/api/v1/users").param("search", "MANAGER").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(put("/api/v1/users/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "manager-new@example.test", "fullName", "Updated Manager"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Manager"));
        login("manager-new@example.test", PASSWORD);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void everyNonAdminRoleCanReadOwnProfileButCannotManageAccounts(Role role) throws Exception {
        UserAccount user = users.saveAndFlush(new UserAccount(
                role.name().toLowerCase() + "-role@example.test",
                role.label(),
                passwords.encode(PASSWORD),
                Set.of(role),
                clock.instant()));
        String token = login(user);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()));
        for (String path : new String[] {"/api/v1/users", "/api/v1/users/" + admin.getId(), "/api/v1/roles"}) {
            mvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "email",
                                "new@example.test",
                                "fullName",
                                "New User",
                                "password",
                                PASSWORD,
                                "roles",
                                Set.of("ADMIN")))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/" + admin.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", admin.getEmail(), "fullName", "Changed"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/" + admin.getId() + "/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("password", "Abcd123!"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/" + user.getId() + "/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roles", Set.of("ADMIN")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleChangesTakeEffectForExistingTokensImmediately() throws Exception {
        String adminToken = login(admin);
        String operatorToken = login(operator);
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(operatorToken)))
                .andExpect(status().isForbidden());
        assignRoles(adminToken, operator.getId(), Set.of("ADMIN", "OPERATOR"));
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(operatorToken)))
                .andExpect(status().isOk());
        assignRoles(adminToken, operator.getId(), Set.of("OPERATOR"));
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(operatorToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastAdministratorCannotLoseTheAdminRole() throws Exception {
        mvc.perform(put("/api/v1/users/" + admin.getId() + "/roles")
                        .header("Authorization", bearer(login(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roles", Set.of("MANAGER")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
    }

    @Test
    void concurrentRoleChangesCannotRemoveBothAdministrators() throws Exception {
        UserAccount secondAdmin = users.saveAndFlush(new UserAccount(
                "second-admin@example.test",
                "Second Administrator",
                admin.getPasswordHash(),
                Set.of(Role.ADMIN),
                clock.instant()));
        String token = login(admin);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> demoteAfterSignal(token, admin.getId(), ready, start));
            var second = executor.submit(() -> demoteAfterSignal(token, secondAdmin.getId(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int firstStatus = first.get(10, TimeUnit.SECONDS);
            int secondStatus = second.get(10, TimeUnit.SECONDS);
            assertThat(new int[] {firstStatus, secondStatus}).contains(200);
            assertThat(firstStatus == 403 || firstStatus == 409 || secondStatus == 403 || secondStatus == 409)
                    .isTrue();
        } finally {
            start.countDown();
        }
        assertThat(users.countUsersWithRole(Role.ADMIN)).isEqualTo(1);
    }

    @Test
    void passwordResetRevokesEveryExistingSessionAndChangesCredentials() throws Exception {
        String adminToken = login(admin);
        String oldSession = login(operator);
        String anotherOldSession = login(operator);
        String newPassword = "FiveBros-New-Password!";
        mvc.perform(put("/api/v1/users/" + operator.getId() + "/password")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("password", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(operator.getId().toString()))
                .andExpect(jsonPath("$.email").value(operator.getEmail()))
                .andExpect(jsonPath("$.fullName").value(operator.getFullName()));
        for (String token : new String[] {oldSession, anotherOldSession}) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", operator.getEmail(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        login(operator.getEmail(), newPassword);
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(login(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "email",
                                "ADMIN@EXAMPLE.TEST",
                                "fullName",
                                "Duplicate",
                                "password",
                                PASSWORD,
                                "roles",
                                Set.of("MANAGER")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void invalidPasswordRolesAndPaginationReturnBadRequest() throws Exception {
        String token = login(admin);
        for (String password : new String[] {"short", "a".repeat(73), "ắ".repeat(25)}) {
            mvc.perform(post("/api/v1/users")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of(
                                    "email",
                                    "invalid@example.test",
                                    "fullName",
                                    "Invalid",
                                    "password",
                                    password,
                                    "roles",
                                    Set.of("MANAGER")))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }
        for (Set<String> roles : Set.of(Set.<String>of(), Set.of("SUPER_ADMIN"))) {
            mvc.perform(put("/api/v1/users/" + operator.getId() + "/roles")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("roles", roles))))
                    .andExpect(status().isBadRequest());
        }
        for (String size : new String[] {"0", "101"}) {
            mvc.perform(get("/api/v1/users").param("size", size).header("Authorization", bearer(token)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/users").param("page", "-1").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanReadAllSixRolesAndUnknownAccountsReturnNotFound() throws Exception {
        String token = login(admin);
        mvc.perform(get("/api/v1/roles").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
        mvc.perform(get("/api/v1/users/" + UUID.randomUUID()).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private void assignRoles(String token, UUID userId, Set<String> roles) throws Exception {
        mvc.perform(put("/api/v1/users/" + userId + "/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roles", roles))))
                .andExpect(status().isOk());
    }

    private int demoteAfterSignal(String token, UUID userId, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent role check did not start");
        }
        return mvc.perform(put("/api/v1/users/" + userId + "/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roles", Set.of("MANAGER")))))
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
