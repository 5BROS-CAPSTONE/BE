package com.fivebros.backend.auth;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fivebros.backend.account.AccountStatus;
import com.fivebros.backend.account.UserAccount;
import com.fivebros.backend.auth.dto.ChangePasswordRequest;
import com.fivebros.backend.common.error.ApiException;
import com.fivebros.backend.security.Role;
import com.fivebros.backend.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChangePasswordIntegrationTest extends ApiIntegrationTest {

    private static final String NEW_PASSWORD = "New-Own-Password!";

    @Autowired private AuthService authentication;
    @Autowired private JwtDecoder decoder;

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleCanChangeItsOwnPasswordAndMustLogInAgain(Role role) throws Exception {
        UserAccount user = users.saveAndFlush(new UserAccount("member@example.test", "Member",
                passwords.encode(PASSWORD), Set.of(role), clock.instant()));
        String token = login(user);
        String otherSession = login(user);
        String adminSession = login(admin);

        mvc.perform(put("/api/v1/auth/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new ChangePasswordRequest(PASSWORD, NEW_PASSWORD))))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        UserAccount updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getPasswordHash()).isNotEqualTo(user.getPasswordHash());
        assertThat(passwords.matches(NEW_PASSWORD, updated.getPasswordHash())).isTrue();
        assertThat(updated.getEmail()).isEqualTo(user.getEmail());
        assertThat(updated.getFullName()).isEqualTo(user.getFullName());
        assertThat(updated.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        for (String revoked : new String[]{token, otherSession}) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(revoked)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(adminSession)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(login(user.getEmail(), NEW_PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value(role.name()));
    }

    @Test
    void wrongCurrentPasswordLeavesPasswordAndSessionsUnchanged() throws Exception {
        String token = login(operator);
        String otherSession = login(operator);
        String response = mvc.perform(put("/api/v1/auth/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new ChangePasswordRequest("wrong-password", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("wrong-password", NEW_PASSWORD);
        assertPasswordAndSessionsUnchanged(token, otherSession);
    }

    @Test
    void missingBlankShortAndOversizedPasswordsAreRejectedWithoutChangingAnything() throws Exception {
        String token = login(operator);
        String otherSession = login(operator);
        for (var request : new ChangePasswordRequest[]{
                new ChangePasswordRequest(null, NEW_PASSWORD),
                new ChangePasswordRequest(" ", NEW_PASSWORD),
                new ChangePasswordRequest("a".repeat(73), NEW_PASSWORD),
                new ChangePasswordRequest(PASSWORD, null),
                new ChangePasswordRequest(PASSWORD, "        "),
                new ChangePasswordRequest(PASSWORD, "1234567"),
                new ChangePasswordRequest(PASSWORD, "a".repeat(73)),
                new ChangePasswordRequest(PASSWORD, "é".repeat(37))}) {
            mvc.perform(put("/api/v1/auth/password").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertPasswordAndSessionsUnchanged(token, otherSession);
    }

    @Test
    void minimumLengthAndUtf8ByteBoundaryAreAccepted() throws Exception {
        String current = PASSWORD;
        for (String next : new String[]{"12345678", "é".repeat(36)}) {
            String token = login(operator.getEmail(), current);
            mvc.perform(put("/api/v1/auth/password").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(new ChangePasswordRequest(current, next))))
                    .andExpect(status().isNoContent());
            assertThat(passwords.matches(next, users.findById(operator.getId()).orElseThrow().getPasswordHash())).isTrue();
            current = next;
        }
        login(operator.getEmail(), current);
    }

    @Test
    void oversizedUtf8CurrentPasswordCannotAuthenticateUsingABcryptPrefix() throws Exception {
        String original = "a".repeat(70) + "é";
        operator.changePassword(passwords.encode(original), clock.instant());
        users.saveAndFlush(operator);
        String token = login(operator.getEmail(), original);
        mvc.perform(put("/api/v1/auth/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new ChangePasswordRequest(original + "é", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
        assertThat(passwords.matches(original, users.findById(operator.getId()).orElseThrow().getPasswordHash())).isTrue();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void missingRevokedAndDisabledAuthenticationCannotChangePassword() throws Exception {
        String request = body(new ChangePasswordRequest(PASSWORD, NEW_PASSWORD));
        mvc.perform(put("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        String revoked = login(operator);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(revoked)))
                .andExpect(status().isNoContent());
        String disabled = login(operator);
        operator.changeStatus(AccountStatus.DISABLED, clock.instant());
        users.saveAndFlush(operator);
        for (String token : new String[]{revoked, disabled}) {
            mvc.perform(put("/api/v1/auth/password").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(users.findById(operator.getId()).orElseThrow().getPasswordHash()).isEqualTo(operator.getPasswordHash());
    }

    @Test
    void accountIsSelectedFromJwtEvenIfTheBodySuppliesAnotherIdAndEmail() throws Exception {
        String adminSession = login(admin);
        mvc.perform(put("/api/v1/auth/password").header("Authorization", bearer(login(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD,
                                "id", admin.getId(), "email", admin.getEmail()))))
                .andExpect(status().isNoContent());
        assertThat(users.findById(admin.getId()).orElseThrow().getPasswordHash()).isEqualTo(admin.getPasswordHash());
        assertThat(passwords.matches(NEW_PASSWORD, users.findById(operator.getId()).orElseThrow().getPasswordHash())).isTrue();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(adminSession)))
                .andExpect(status().isOk());
    }

    @Test
    void serviceRechecksSessionAfterAConcurrentResetOrRevocation() throws Exception {
        var jwt = decoder.decode(login(operator));
        var otherJwt = decoder.decode(login(admin));
        UUID sessionId = UUID.fromString(jwt.getId());
        mvc.perform(put("/api/v1/users/{id}/password", operator.getId())
                        .header("Authorization", bearer(otherJwt.getTokenValue()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("password", NEW_PASSWORD))))
                .andExpect(status().isOk());
        for (UUID invalidSession : new UUID[]{sessionId, UUID.fromString(otherJwt.getId()), UUID.randomUUID()}) {
            assertThatThrownBy(() -> authentication.changePassword(operator.getId(), invalidSession,
                    new ChangePasswordRequest(PASSWORD, NEW_PASSWORD)))
                    .isInstanceOfSatisfying(ApiException.class, error -> {
                        assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(error.code()).isEqualTo("UNAUTHENTICATED");
                    });
        }
        assertThat(passwords.matches(NEW_PASSWORD, users.findById(operator.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void requestToStringDoesNotExposePasswords() {
        assertThat(new ChangePasswordRequest(PASSWORD, NEW_PASSWORD).toString())
                .contains("REDACTED").doesNotContain(PASSWORD, NEW_PASSWORD);
    }

    private void assertPasswordAndSessionsUnchanged(String... tokens) throws Exception {
        assertThat(users.findById(operator.getId()).orElseThrow().getPasswordHash()).isEqualTo(operator.getPasswordHash());
        for (String token : tokens) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                    .andExpect(status().isOk());
        }
    }
}
