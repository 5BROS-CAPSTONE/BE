package com.fivebros.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.fivebros.backend.support.ApiIntegrationTest;

/**
 * Vercel sinh một domain preview mới cho mỗi pull request, nên danh sách origin phải khai báo
 * được bằng mẫu. Test này khoá hành vi đó lại: đổi về setAllowedOrigins là hai ca đầu đỏ ngay.
 */
@TestPropertySource(
        properties = {
            "app.cors.allowed-origins=https://frontendcapstone-eight.vercel.app,https://frontendcapstone-*.vercel.app"
        })
class CorsPreviewOriginIntegrationTest extends ApiIntegrationTest {

    @Test
    void previewDomainMatchingThePatternIsAllowed() throws Exception {
        String preview = "https://frontendcapstone-6p31me5hg-phatnt284s-projects.vercel.app";
        mvc.perform(options("/api/v1/auth/login")
                        .header("Origin", preview)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", preview));
    }

    @Test
    void productionDomainWithoutWildcardStillMatchesExactly() throws Exception {
        String production = "https://frontendcapstone-eight.vercel.app";
        mvc.perform(options("/api/v1/auth/login")
                        .header("Origin", production)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", production));
    }

    @Test
    void lookalikeDomainOutsideThePatternIsStillRejected() throws Exception {
        for (String origin : new String[] {
            "https://frontendcapstone-eight.vercel.app.evil.example",
            "https://evil-frontendcapstone-eight.vercel.app",
            "http://frontendcapstone-eight.vercel.app"
        }) {
            mvc.perform(options("/api/v1/auth/login")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden());
        }
    }
}
