package com.fivebros.backend.account;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationIntegrationTest extends ApiIntegrationTest {

    @Autowired private JwtDecoder decoder;
    @Autowired private UserAccountService accounts;

    @Test
    void registrationRoleOptionsArePublicAndExcludeAdmin() throws Exception {
        String response = mvc.perform(get("/api/v1/auth/registration-roles"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("ADMIN", "permissions");
        for (Role role : Role.values()) {
            if (role != Role.ADMIN) {
                assertThat(response).contains(role.name());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void registrationStoresRequestedRoleWithoutGrantingPermissionsOrCreatingASession(Role role) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(registrationBody("NEW@EXAMPLE.TEST", role.name()))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.email").value("new@example.test"))
                .andExpect(jsonPath("$.fullName").value("New Applicant"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedRole").value(role.name()))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UserAccount user = users.findById(UUID.fromString(read(response).get("id").asString())).orElseThrow();
        assertThat(accounts.get(user.getId()).roles()).isEmpty();
        assertThat(user.getReviewedBy()).isNull();
        assertThat(passwords.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(sessions.count()).isZero();
    }

    @Test
    void approvalGrantsADifferentRoleAndUpdatesTheExistingPendingSession() throws Exception {
        UUID id = register("applicant@example.test", "MANAGER");
        String applicantToken = login("applicant@example.test", PASSWORD);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(applicantToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.roles").isEmpty());
        String token = login(admin);
        mvc.perform(post("/api/v1/users/" + id + "/approve").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roles", Set.of("OPERATOR", "ANALYST")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.requestedRole").value("MANAGER"))
                .andExpect(jsonPath("$.reviewedBy").value(admin.getId().toString()))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
        UserAccount saved = users.findById(id).orElseThrow();
        assertThat(accounts.get(saved.getId()).roles()).containsExactlyInAnyOrder(Role.OPERATOR, Role.ANALYST);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(applicantToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles.length()").value(2));
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(applicantToken)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/" + id + "/roles").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roles", Set.of("ADMIN", "OPERATOR")))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(applicantToken)))
                .andExpect(status().isOk());
    }

    @Test
    void pendingUsersCanLogInReadTheirProfileAndLogOutButCannotUseBusinessEndpoints() throws Exception {
        UUID id = register("applicant@example.test", "OPERATOR");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "applicant@example.test", "password", "Wrong-password!"))))
                .andExpect(status().isUnauthorized());
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "applicant@example.test", "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.status").value("PENDING"))
                .andExpect(jsonPath("$.user.roles").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String token = read(response).get("accessToken").asString();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestedRole").value("OPERATOR"));
        for (String path : new String[]{"/api/v1/users", "/api/v1/users/" + id, "/api/v1/roles",
                "/api/v1/future-business-endpoint"}) {
            mvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/users/" + id + "/approve").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roles", Set.of("OPERATOR")))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/" + id + "/reject").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Self rejection"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/" + id + "/roles").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roles", Set.of("ADMIN")))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectionRevokesEveryPendingSessionAndPreventsSubsequentLogins() throws Exception {
        UUID id = register("applicant@example.test", "OPERATOR");
        String firstToken = login("applicant@example.test", PASSWORD);
        String secondToken = login("applicant@example.test", PASSWORD);
        mvc.perform(post("/api/v1/users/" + id + "/reject").header("Authorization", bearer(login(admin)))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", " Identity not verified "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.rejectionReason").value("Identity not verified"))
                .andExpect(jsonPath("$.reviewedBy").value(admin.getId().toString()))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
        assertLoginBlocked("applicant@example.test", "ACCOUNT_REJECTED");
        for (String token : new String[]{firstToken, secondToken}) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                    .andExpect(status().isUnauthorized());
            UUID sessionId = UUID.fromString(decoder.decode(token).getId());
            assertThat(sessions.findById(sessionId).orElseThrow().isActiveAt(clock.instant())).isFalse();
        }
        assertThat(sessions.count()).isEqualTo(3);
    }

    @Test
    void pendingAccountsHaveNoEffectiveRolesEvenIfRolesExistInTheDatabase() throws Exception {
        UserAccount pending = UserAccount.pendingRegistration("applicant@example.test", "Applicant",
                passwords.encode(PASSWORD), Role.OPERATOR, clock.instant());
        pending.replaceRoles(Set.of(Role.ADMIN), clock.instant());
        users.saveAndFlush(pending);
        UUID id = pending.getId();
        String token = login(pending);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.roles").isEmpty());
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/" + id + "/approve").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roles", Set.of("OPERATOR")))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/" + id + "/reject").header("Authorization", bearer(login(admin)))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Invalid identity"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientSuppliedRolesStatusAndReviewerCannotBypassApproval() throws Exception {
        Map<String, Object> request = registrationBody("applicant@example.test", "OPERATOR");
        request.put("roles", Set.of("ADMIN"));
        request.put("status", "ACTIVE");
        request.put("reviewedBy", admin.getId());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        UserAccount saved = users.findByEmail("applicant@example.test").orElseThrow();
        assertThat(accounts.get(saved.getId()).roles()).isEmpty();
        assertThat(saved.getReviewedBy()).isNull();
        String token = login(saved.getEmail(), PASSWORD);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.roles").isEmpty());
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanFilterTheReviewQueueByStatusAndSearchTogether() throws Exception {
        register("first-pending@example.test", "OPERATOR");
        register("second-pending@example.test", "TECHNICIAN");
        String token = login(admin);
        mvc.perform(get("/api/v1/users").param("status", "PENDING").param("size", "1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].roles").isEmpty())
                .andExpect(jsonPath("$.items[0].requestedRole").isNotEmpty());
        mvc.perform(get("/api/v1/users").param("status", "PENDING").param("search", "FIRST")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/users").param("status", "ACTIVE").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/users").param("status", "UNKNOWN").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void nonAdminsCannotApproveOrRejectRegistrations(Role role) throws Exception {
        UUID id = register("applicant@example.test", "OPERATOR");
        UserAccount reviewer = users.saveAndFlush(new UserAccount(role.name().toLowerCase() + "-reviewer@example.test",
                role.label(), admin.getPasswordHash(), Set.of(role), clock.instant()));
        String token = login(reviewer);
        mvc.perform(post("/api/v1/users/" + id + "/approve").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roles", Set.of("OPERATOR")))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/" + id + "/reject").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Rejected"))))
                .andExpect(status().isForbidden());
        assertThat(users.findById(id).orElseThrow().getStatus()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void anonymousApplicantsCannotReadTheQueueOrReviewAnAccount() throws Exception {
        UUID id = register("applicant@example.test", "OPERATOR");
        mvc.perform(get("/api/v1/users").param("status", "PENDING")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roles", Set.of("OPERATOR")))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("reason", "Rejected"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleAssignmentCannotActivatePendingOrRejectedAccounts() throws Exception {
        UUID id = register("applicant@example.test", "OPERATOR");
        String token = login(admin);
        for (boolean rejected : new boolean[]{false, true}) {
            if (rejected) {
                mvc.perform(post("/api/v1/users/" + id + "/reject").header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Rejected"))))
                        .andExpect(status().isOk());
            }
            mvc.perform(put("/api/v1/users/" + id + "/roles").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roles", Set.of("ADMIN")))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("REGISTRATION_REVIEW_REQUIRED"));
            assertThat(accounts.get(id).roles()).isEmpty();
        }
    }

    @Test
    void registrationRejectsInvalidFieldsAndAdminRequests() throws Exception {
        Map<String, Object> invalidValues = Map.of("email", "invalid-email", "fullName", " ",
                "password", "short", "requestedRole", "UNKNOWN");
        for (var entry : invalidValues.entrySet()) {
            Map<String, Object> request = registrationBody("applicant@example.test", "OPERATOR");
            request.put(entry.getKey(), entry.getValue());
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(request)))
                    .andExpect(status().isBadRequest());
        }
        Map<String, Object> missingRole = registrationBody("applicant@example.test", "OPERATOR");
        missingRole.remove("requestedRole");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(missingRole)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.requestedRole").exists());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(registrationBody("applicant@example.test", "ADMIN"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUESTED_ROLE"));
        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void duplicateEmailsCannotOverwriteActivePendingOrRejectedAccounts() throws Exception {
        UUID pendingId = register("applicant@example.test", "OPERATOR");
        String token = login(admin);
        for (String email : new String[]{"ADMIN@EXAMPLE.TEST", "APPLICANT@EXAMPLE.TEST"}) {
            assertDuplicateRegistration(email);
        }
        mvc.perform(post("/api/v1/users/" + pendingId + "/reject").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Rejected"))))
                .andExpect(status().isOk());
        assertDuplicateRegistration("APPLICANT@EXAMPLE.TEST");
        assertThat(users.findById(pendingId).orElseThrow().getRequestedRole()).isEqualTo(Role.OPERATOR);
    }

    @Test
    void approvalRequiresValidNonAdminRolesAndRejectionRequiresAReason() throws Exception {
        UUID id = register("applicant@example.test", "OPERATOR");
        String token = login(admin);
        for (Map<String, Object> request : List.<Map<String, Object>>of(
                Map.of(), Map.of("roles", Set.of()), Map.of("roles", Set.of("UNKNOWN")),
                Map.of("roles", Set.of("ADMIN")), Map.of("roles", Set.of("OPERATOR", "ADMIN")),
                Map.of("roles", Arrays.asList("OPERATOR", null)))) {
            mvc.perform(post("/api/v1/users/" + id + "/approve").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body(request)))
                    .andExpect(status().isBadRequest());
        }
        for (Map<String, Object> request : List.<Map<String, Object>>of(
                Map.of(), Map.of("reason", " "), Map.of("reason", "a".repeat(501)))) {
            mvc.perform(post("/api/v1/users/" + id + "/reject").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body(request)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(users.findById(id).orElseThrow().getStatus()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void aRegistrationCanOnlyBeReviewedOnceAndActiveAccountsCannotBeReviewed() throws Exception {
        UUID id = register("applicant@example.test", "OPERATOR");
        UUID rejectedId = register("rejected@example.test", "TECHNICIAN");
        String token = login(admin);
        mvc.perform(post("/api/v1/users/" + id + "/approve").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roles", Set.of("OPERATOR")))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/users/" + rejectedId + "/reject").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Rejected"))))
                .andExpect(status().isOk());
        for (UUID target : new UUID[]{id, rejectedId, operator.getId()}) {
            mvc.perform(post("/api/v1/users/" + target + "/approve").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roles", Set.of("MANAGER")))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_ALREADY_REVIEWED"));
            mvc.perform(post("/api/v1/users/" + target + "/reject").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Rejected"))))
                    .andExpect(status().isConflict());
        }
        mvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/approve").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roles", Set.of("OPERATOR")))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/reject").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("reason", "Rejected"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentApprovalAndRejectionProduceExactlyOneDecision() throws Exception {
        UUID id = register("applicant@example.test", "OPERATOR");
        String token = login(admin);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var approval = executor.submit(() -> reviewAfterSignal(token, id, "approve",
                    Map.of("roles", Set.of("OPERATOR")), ready, start));
            var rejection = executor.submit(() -> reviewAfterSignal(token, id, "reject",
                    Map.of("reason", "Rejected"), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new int[]{approval.get(10, TimeUnit.SECONDS), rejection.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
        }
        UserAccount saved = users.findById(id).orElseThrow();
        assertThat(saved.getReviewedBy()).isEqualTo(admin.getId());
        if (saved.isActive()) {
            assertThat(accounts.get(id).roles()).containsExactly(Role.OPERATOR);
            assertThat(saved.getRejectionReason()).isNull();
        } else {
            assertThat(saved.getStatus()).isEqualTo(AccountStatus.REJECTED);
            assertThat(accounts.get(id).roles()).isEmpty();
            assertThat(saved.getRejectionReason()).isEqualTo("Rejected");
        }
    }

    private Map<String, Object> registrationBody(String email, String role) {
        return new HashMap<>(Map.of("email", email, "fullName", " New Applicant ",
                "password", PASSWORD, "requestedRole", role));
    }

    private UUID register(String email, String role) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(registrationBody(email, role))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(read(response).get("id").asString());
    }

    private void assertDuplicateRegistration(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(registrationBody(email, "TECHNICIAN"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    private void assertLoginBlocked(String email, String code) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", "Wrong-password!"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(code));
    }

    private int reviewAfterSignal(String token, UUID id, String action, Map<String, Object> request,
                                 CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent review did not start");
        }
        return mvc.perform(post("/api/v1/users/" + id + "/" + action).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(request)))
                .andReturn().getResponse().getStatus();
    }
}
