package com.fivebros.backend.account;

import java.util.Map;
import java.util.Set;

import com.fivebros.backend.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordPolicyIntegrationTest extends ApiIntegrationTest {

    @Test
    void eightCharacterPasswordsWorkForRegistrationAdminCreationAndPasswordReset() throws Exception {
        String password = "Abcd123!";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "registered@example.test", "fullName", "Registered User",
                                "password", password, "requestedRole", "OPERATOR"))))
                .andExpect(status().isCreated());
        login("registered@example.test", password);

        String token = login(admin);
        mvc.perform(post("/api/v1/users").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "created@example.test", "fullName", "Created User",
                                "password", password, "roles", Set.of("OPERATOR")))))
                .andExpect(status().isCreated());
        login("created@example.test", password);

        mvc.perform(put("/api/v1/users/" + operator.getId()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", operator.getEmail(), "fullName", "Operator", "password", password))))
                .andExpect(status().isOk());
        login(operator.getEmail(), password);
    }

    @Test
    void sevenCharacterPasswordsAreRejectedWithoutCreatingAccountsOrChangingCredentials() throws Exception {
        String password = "Abc123!";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "invalid@example.test", "fullName", "Invalid User",
                                "password", password, "requestedRole", "OPERATOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value(containsString("at least 8 characters")));

        String token = login(admin);
        mvc.perform(post("/api/v1/users").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "invalid@example.test", "fullName", "Invalid User",
                                "password", password, "roles", Set.of("OPERATOR")))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/users/" + operator.getId()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", operator.getEmail(), "fullName", "Operator", "password", password))))
                .andExpect(status().isBadRequest());
        assertThat(users.count()).isEqualTo(2);
        login(operator);
    }

    @Test
    void unicodePasswordsRespectBothTheCharacterMinimumAndTheUtf8ByteMaximum() throws Exception {
        String[] accepted = {"ắ".repeat(8), "ắ".repeat(24), "😀".repeat(8)};
        String[] rejected = {"ắ".repeat(7), "ắ".repeat(25), "😀".repeat(7), "😀".repeat(19)};
        for (int index = 0; index < accepted.length; index++) {
            String email = "unicode-" + index + "@example.test";
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", email, "fullName", "Unicode User", "password", accepted[index],
                                    "requestedRole", "OPERATOR"))))
                    .andExpect(status().isCreated());
            login(email, accepted[index]);
        }
        for (String password : rejected) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", "rejected-unicode@example.test", "fullName", "Invalid User",
                                    "password", password, "requestedRole", "OPERATOR"))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").exists());
        }
    }
}
