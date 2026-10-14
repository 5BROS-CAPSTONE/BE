package com.fivebros.backend.auth;

import com.fivebros.backend.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

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
                .andExpect(jsonPath("$.paths['/api/v1/auth/register']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/registration-roles']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password'].put.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password'].put.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password'].put.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}/approve']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}/reject']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}/password'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/error']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.Jwt").doesNotExist());
    }

    @Test
    void requestSchemasDescribeAdminProvisioningWithoutRegistrationOrReview() throws Exception {
        String response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CreateUserRequest.properties.email.example").value("manager@example.com"))
                .andExpect(jsonPath("$.components.schemas.CreateUserRequest.properties.password.minLength").value(8))
                .andExpect(jsonPath("$.components.schemas.ResetPasswordRequest.properties.password.minLength").value(8))
                .andExpect(jsonPath("$.components.schemas.UpdateUserRequest.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateUserRequest.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.ResetPasswordRequest.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.ChangePasswordRequest.properties.currentPassword.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.ChangePasswordRequest.properties.newPassword.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.ChangePasswordRequest.properties.newPassword.minLength").value(8))
                .andExpect(jsonPath("$.components.schemas.ChangePasswordRequest.properties.newPassword.maxLength").value(72))
                .andExpect(jsonPath("$.components.schemas.ChangePasswordRequest.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ChangePasswordRequest.properties.email").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.RegisterUserRequest").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ApproveRegistrationRequest").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.RejectRegistrationRequest").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.requestedRole").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.reviewedBy").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var schemas = read(response).get("components").get("schemas");
        var roles = schemas.get("CreateUserRequest").get("properties").get("roles").get("items").get("enum");
        assertThat(roles.size()).isEqualTo(6);
        assertThat(roles.toString()).contains("ADMIN", "OPERATOR");
        var statuses = schemas.get("UserResponse").get("properties").get("status").get("enum");
        assertThat(statuses.toString()).contains("ACTIVE", "DISABLED").doesNotContain("PENDING", "REJECTED");
    }

    @Test
    void exposingSwaggerDoesNotGrantAccessToProtectedApis() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v3/api-docs")).andExpect(status().isUnauthorized());
        String token = login(operator);
        mvc.perform(get("/v3/api-docs").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }
}
