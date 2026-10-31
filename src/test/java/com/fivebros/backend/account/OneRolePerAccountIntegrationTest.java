package com.fivebros.backend.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fivebros.backend.support.ApiIntegrationTest;

/**
 * BPMN "S5 - Người dùng &amp; Phân quyền / Phân quyền" cho Admin chọn đúng một role. Các test
 * này giữ API khớp với tài liệu, vì tập role vẫn là Set nên rất dễ lỡ tay nhận nhiều hơn một.
 */
class OneRolePerAccountIntegrationTest extends ApiIntegrationTest {

    private String createWithRoles(String token, String email, Set<String> roles) throws Exception {
        return mvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "email", email,
                                "fullName", "Nguoi Dung Thu",
                                "password", PASSWORD,
                                "roles", roles))))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void rejectsCreatingAnAccountWithTwoRoles() throws Exception {
        String token = login(admin);
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "email",
                                "hai-vai-tro@example.test",
                                "fullName",
                                "Hai Vai Tro",
                                "password",
                                PASSWORD,
                                "roles",
                                Set.of("MANAGER", "ANALYST")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsCreatingAnAccountWithNoRole() throws Exception {
        String token = login(admin);
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "email",
                                "khong-vai-tro@example.test",
                                "fullName",
                                "Khong Vai Tro",
                                "password",
                                PASSWORD,
                                "roles",
                                Set.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsExactlyOneRole() throws Exception {
        String token = login(admin);
        createWithRoles(token, "mot-vai-tro@example.test", Set.of("OPERATOR"));
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "email",
                                "mot-vai-tro-2@example.test",
                                "fullName",
                                "Mot Vai Tro",
                                "password",
                                PASSWORD,
                                "roles",
                                Set.of("ANALYST")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("ANALYST"));
    }

    @Test
    void rejectsAssigningTwoRolesToAnExistingAccount() throws Exception {
        String token = login(admin);
        String created = createWithRoles(token, "doi-vai-tro@example.test", Set.of("TECHNICIAN"));
        String id = read(created).get("id").asString();

        mvc.perform(put("/api/v1/users/" + id + "/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roles", Set.of("TECHNICIAN", "OPERATOR")))))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/v1/users/" + id + "/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roles", Set.of("OPERATOR")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("OPERATOR"));
    }
}
