package com.fivebros.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.fivebros.backend.config.JwtProperties;
import com.fivebros.backend.support.ApiIntegrationTest;

class AuthIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtDecoder decoder;

    @Autowired
    private JwtProperties properties;

    @Test
    void loginReturnsTokenAndProfileWithoutPassword() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "ADMIN@EXAMPLE.TEST", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.email").value(admin.getEmail()))
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownAccountHaveTheSameError() throws Exception {
        for (String email : new String[] {admin.getEmail(), "unknown@example.test"}) {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", email, "password", "incorrect-password"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("Email or password is incorrect"));
        }
        assertThat(sessions.count()).isZero();
    }

    @Test
    void protectedEndpointsRequireAnAccessToken() throws Exception {
        for (String path : new String[] {"/api/v1/auth/me", "/api/v1/users", "/api/v1/roles"}) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    void logoutRevokesOnlyTheCurrentSession() throws Exception {
        String token = login(operator);
        String otherSession = login(operator);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(otherSession)))
                .andExpect(status().isOk());
    }

    @Test
    void tamperedTokenCannotAuthenticate() throws Exception {
        String token = login(admin);
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtIsRejectedEvenInsideTheDefaultClockSkew() throws Exception {
        var original = decoder.decode(login(admin));
        String expired = sign(
                admin.getId().toString(),
                original.getId(),
                properties.issuer(),
                clock.instant().minusSeconds(1));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedTokenMustHaveAValidIssuerSessionAndSubject() throws Exception {
        var original = decoder.decode(login(admin));
        Instant expires = clock.instant().plusSeconds(600);
        String wrongIssuer = sign(admin.getId().toString(), original.getId(), "other-application", expires);
        String unknownSession =
                sign(admin.getId().toString(), UUID.randomUUID().toString(), properties.issuer(), expires);
        String wrongSubject = sign(operator.getId().toString(), original.getId(), properties.issuer(), expires);
        String missingExpiry = sign(admin.getId().toString(), original.getId(), properties.issuer(), null);
        for (String token : new String[] {wrongIssuer, unknownSession, wrongSubject, missingExpiry}) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void corsAllowsOnlyConfiguredFrontendOrigins() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private String sign(String subject, String sessionId, String issuer, Instant expires) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .id(sessionId)
                .issuedAt(clock.instant().minusSeconds(120));
        if (expires != null) {
            claims.expiresAt(expires);
        }
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims.build()))
                .getTokenValue();
    }
}
