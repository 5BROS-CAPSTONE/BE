package com.fivebros.backend.account;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fivebros.backend.security.Role;
import com.fivebros.backend.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalAccountIntegrationTest extends ApiIntegrationTest {

    @Test
    void registrationAndReviewRoutesAreUnavailableAndCannotCreateAnAccount() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/registration-roles")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        String token = login(admin);
        for (String path : new String[]{"/api/v1/auth/register", "/api/v1/users/" + operator.getId() + "/approve",
                "/api/v1/users/" + operator.getId() + "/reject"}) {
            mvc.perform(post(path).header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/auth/registration-roles").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
        assertThat(users.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void adminProvidedCredentialsAndRolesAreUsableImmediately(Role role) throws Exception {
        String email = role.name().toLowerCase() + "-provisioned@example.test";
        String response = mvc.perform(post("/api/v1/users").header("Authorization", bearer(login(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "fullName", "Internal User",
                                "password", PASSWORD, "roles", Set.of(role.name())))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value(role.name()))
                .andExpect(jsonPath("$.requestedRole").doesNotExist())
                .andExpect(jsonPath("$.reviewedBy").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(read(response).get("id").asString());
        UserAccount saved = users.findById(id).orElseThrow();
        assertThat(passwords.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(login(email, PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value(role.name()));
    }

    @Test
    void creatingAnInternalAccountRequiresAtLeastOneKnownRole() throws Exception {
        String token = login(admin);
        for (Set<String> roles : Set.of(Set.<String>of(), Set.of("UNKNOWN"))) {
            mvc.perform(post("/api/v1/users").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", "new@example.test", "fullName", "New User",
                                    "password", PASSWORD, "roles", roles))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/users").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "new@example.test", "fullName", "New User", "password", PASSWORD))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.roles").exists());
        assertThat(users.count()).isEqualTo(2);
        String roleResponse = mvc.perform(get("/api/v1/roles").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(roleResponse).doesNotContain("user:review");
    }

    @Test
    void disablingAnAccountRevokesSessionsAndReactivationRequiresFreshLogin() throws Exception {
        String token = login(admin);
        String previousSession = login(operator);
        updateStatus(token, operator, "DISABLED").andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(previousSession)))
                .andExpect(status().isUnauthorized());
        assertLoginBlocked(operator.getEmail(), PASSWORD, 403, "ACCOUNT_DISABLED");
        assertLoginBlocked(operator.getEmail(), "Wrong-password!", 401, "INVALID_CREDENTIALS");
        mvc.perform(get("/api/v1/users").param("status", "DISABLED").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        String newPassword = "Admin-Provided-New-Password!";
        mvc.perform(put("/api/v1/users/" + operator.getId() + "/password")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("password", newPassword))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        updateStatus(token, operator, "ACTIVE").andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(previousSession)))
                .andExpect(status().isUnauthorized());
        assertLoginBlocked(operator.getEmail(), PASSWORD, 401, "INVALID_CREDENTIALS");
        login(operator.getEmail(), newPassword);
    }

    @Test
    void adminCanProvisionALegacyDisabledAccountButMustAssignRolesBeforeActivation() throws Exception {
        UserAccount legacy = new UserAccount("legacy@example.test", "Legacy Applicant",
                passwords.encode(PASSWORD), Set.of(), clock.instant());
        legacy.changeStatus(AccountStatus.DISABLED, clock.instant());
        users.saveAndFlush(legacy);
        String token = login(admin);
        updateStatus(token, legacy, "ACTIVE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ROLES_REQUIRED"));
        mvc.perform(put("/api/v1/users/" + legacy.getId() + "/roles").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roles", Set.of("OPERATOR")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.roles[0]").value("OPERATOR"));
        assertLoginBlocked(legacy.getEmail(), PASSWORD, 403, "ACCOUNT_DISABLED");
        updateStatus(token, legacy, "ACTIVE").andExpect(status().isOk());
        login(legacy.getEmail(), PASSWORD);
    }

    @Test
    void disabledAdministratorsDoNotAllowDisablingOrDemotingTheLastActiveAdministrator() throws Exception {
        UserAccount disabledAdmin = new UserAccount("disabled-admin@example.test", "Disabled Admin",
                admin.getPasswordHash(), Set.of(Role.ADMIN), clock.instant());
        disabledAdmin.changeStatus(AccountStatus.DISABLED, clock.instant());
        users.saveAndFlush(disabledAdmin);
        String token = login(admin);
        updateStatus(token, admin, "DISABLED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
        mvc.perform(put("/api/v1/users/" + admin.getId() + "/roles").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roles", Set.of("OPERATOR")))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
        assertThat(users.countActiveUsersWithRole(Role.ADMIN)).isEqualTo(1);
    }

    @Test
    void concurrentDisableAndDemotionCannotRemoveBothActiveAdministrators() throws Exception {
        UserAccount second = users.saveAndFlush(new UserAccount("second-admin@example.test", "Second Admin",
                admin.getPasswordHash(), Set.of(Role.ADMIN), clock.instant()));
        String token = login(admin);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var disabling = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return updateStatus(token, second, "DISABLED").andReturn().getResponse().getStatus();
            });
            var demoting = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return mvc.perform(put("/api/v1/users/" + admin.getId() + "/roles")
                                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                                .content(body(Map.of("roles", Set.of("OPERATOR")))))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int firstStatus = disabling.get(10, TimeUnit.SECONDS);
            int secondStatus = demoting.get(10, TimeUnit.SECONDS);
            assertThat(new int[]{firstStatus, secondStatus}).contains(200);
            assertThat(firstStatus == 403 || firstStatus == 409 || secondStatus == 403 || secondStatus == 409).isTrue();
        } finally {
            start.countDown();
        }
        assertThat(users.countActiveUsersWithRole(Role.ADMIN)).isEqualTo(1);
    }

    private ResultActions updateStatus(String token, UserAccount user, String accountStatus) throws Exception {
        return mvc.perform(put("/api/v1/users/" + user.getId()).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", user.getEmail(), "fullName", user.getFullName(), "status", accountStatus))));
    }

    private void assertLoginBlocked(String email, String password, int httpStatus, String code) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", password))))
                .andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code));
    }
}
