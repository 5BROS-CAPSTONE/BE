package com.fivebros.backend.support;

import java.time.Clock;
import java.util.Map;
import java.util.Set;

import com.fivebros.backend.account.UserAccount;
import com.fivebros.backend.account.UserAccountRepository;
import com.fivebros.backend.auth.AuthSessionRepository;
import com.fivebros.backend.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.config.import=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiIntegrationTest {

    protected static final String PASSWORD = "FiveBros-Test-Password!";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserAccountRepository users;
    @Autowired protected AuthSessionRepository sessions;
    @Autowired protected PasswordEncoder passwords;
    @Autowired protected Clock clock;

    protected UserAccount admin;
    protected UserAccount operator;

    @BeforeEach
    void prepareAccounts() {
        sessions.deleteAllInBatch();
        users.deleteAll();
        String hash = passwords.encode(PASSWORD);
        admin = users.saveAndFlush(new UserAccount("admin@example.test", "Administrator", hash,
                Set.of(Role.ADMIN), clock.instant()));
        operator = users.saveAndFlush(new UserAccount("operator@example.test", "Operator", hash,
                Set.of(Role.OPERATOR), clock.instant()));
    }

    protected String login(UserAccount user) throws Exception {
        return login(user.getEmail(), PASSWORD);
    }

    protected String login(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return read(response).get("accessToken").asString();
    }

    protected String body(Object value) {
        return json.writeValueAsString(value);
    }

    protected JsonNode read(String value) {
        return json.readTree(value);
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
