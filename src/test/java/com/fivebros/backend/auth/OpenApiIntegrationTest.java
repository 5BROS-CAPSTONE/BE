package com.fivebros.backend.auth;

import java.util.Map;

import com.fivebros.backend.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiIntegrationTest extends ApiIntegrationTest {

    @Test
    void swaggerUiAssetsAndConfigurationAreAccessibleWithoutLoggingIn() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Swagger UI")));
        for (String path : new String[]{"/swagger-ui/swagger-ui.css", "/swagger-ui/swagger-ui-bundle.js",
                "/swagger-ui/swagger-initializer.js"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
        mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("openapi:")));
    }

    @Test
    void openApiDescribesBearerAuthenticationPublicOperationsAndHidesTheJwtPrincipal() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("5BROS Backend API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/registration-roles'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}/approve'].post.parameters.length()").value(1))
                .andExpect(jsonPath("$.paths['/error']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.Jwt").doesNotExist());
    }

    @Test
    void requestSchemasContainUsableExamplesPasswordRulesAndNonAdminRoleChoices() throws Exception {
        String response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.RegisterUserRequest.properties.email.example").value("pilot@example.com"))
                .andExpect(jsonPath("$.components.schemas.RegisterUserRequest.properties.password.minLength").value(8))
                .andExpect(jsonPath("$.components.schemas.CreateUserRequest.properties.password.minLength").value(8))
                .andExpect(jsonPath("$.components.schemas.UpdateUserRequest.properties.password.minLength").value(8))
                .andExpect(jsonPath("$.components.schemas.RegisterUserRequest.properties.password.writeOnly").value(true))
                .andReturn().getResponse().getContentAsString();
        var schemas = read(response).get("components").get("schemas");
        var registrationRoles = schemas.get("RegisterUserRequest").get("properties").get("requestedRole").get("enum");
        assertThat(registrationRoles.size()).isEqualTo(5);
        assertThat(registrationRoles.toString()).contains("OPERATOR").doesNotContain("ADMIN");
        var approvedRoles = schemas.get("ApproveRegistrationRequest").get("properties").get("roles").get("items").get("enum");
        assertThat(approvedRoles.size()).isEqualTo(5);
        assertThat(approvedRoles.toString()).doesNotContain("ADMIN");
    }

    @Test
    void exposingSwaggerDoesNotGrantAccessToProtectedApis() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "pending-docs@example.test", "fullName", "Pending User",
                                "password", PASSWORD, "requestedRole", "OPERATOR"))))
                .andExpect(status().isCreated());
        String token = login("pending-docs@example.test", PASSWORD);
        mvc.perform(get("/v3/api-docs").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }
}
