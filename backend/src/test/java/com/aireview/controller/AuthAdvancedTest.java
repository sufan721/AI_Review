package com.aireview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;

import com.aireview.security.AuthStateStore;
import com.aireview.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class AuthAdvancedTest extends AbstractIntegrationTest {
    private static final String CAPTCHA_KEY = "test-captcha";
    private static final String PASSWORD = "Secret123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthStateStore store;

    @BeforeEach
    void clearAuthTables() {
        jdbcTemplate.execute("TRUNCATE TABLE `sys_user_role`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_user_sso_binding`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_login_log`");
        jdbcTemplate.execute("TRUNCATE TABLE `user`");
    }

    @Test
    void ssoMockPageIsPublicAndLinksBackToFrontend() throws Exception {
        String html = mockMvc.perform(get("/api/auth/sso/mock"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("sso-callback").doesNotContain("__FRONTEND_BASE__");
    }

    @Test
    void ssoLoginBindsSubjectAndReusesExistingUser() throws Exception {
        String first = mockMvc.perform(post("/api/auth/sso/login")
                .contentType(APPLICATION_JSON)
                .content(ssoLogin("OIDC", "demo-user-1001", "sso_demo", "demo@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.user.username").value("sso_demo"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        long firstId = objectMapper.readTree(first).path("data").path("user").path("id").asLong();

        String second = mockMvc.perform(post("/api/auth/sso/login")
                .contentType(APPLICATION_JSON)
                .content(ssoLogin("OIDC", "demo-user-1001", "sso_demo", "demo@example.com")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long secondId = objectMapper.readTree(second).path("data").path("user").path("id").asLong();

        assertThat(secondId).isEqualTo(firstId);
        Integer bindings = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `sys_user_sso_binding` WHERE sso_provider = 'OIDC' AND sso_subject = 'demo-user-1001'",
            Integer.class);
        assertThat(bindings).isEqualTo(1);
    }

    @Test
    void refreshTokenRotatesAndCannotBeReplayed() throws Exception {
        JsonNode first = register("dave");
        String refreshToken = first.path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.createObjectNode().put("refreshToken", refreshToken).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.createObjectNode().put("refreshToken", refreshToken).toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutBlacklistsAccessToken() throws Exception {
        String token = register("eve").path("accessToken").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointRequiresAdminRole() throws Exception {
        String token = register("frank").path("accessToken").asText();

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        Long frankId = jdbcTemplate.queryForObject(
            "SELECT id FROM `user` WHERE username = 'frank'", Long.class);
        jdbcTemplate.update(
            "INSERT INTO sys_user_role(user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code = 'ROLE_ADMIN'",
            frankId);

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    private JsonNode register(String username) throws Exception {
        ObjectNode node = objectMapper.createObjectNode()
            .put("username", username)
            .put("password", PASSWORD);
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(node.toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private void seedCaptcha() {
        store.put("captcha:" + CAPTCHA_KEY, "7", Duration.ofMinutes(5));
    }

    private String ssoLogin(String provider, String subject, String username, String email) {
        return objectMapper.createObjectNode()
            .put("provider", provider)
            .put("subject", subject)
            .put("username", username)
            .put("email", email)
            .toString();
    }

    private String loginCredentials(String username, String password) {
        return objectMapper.createObjectNode()
            .put("username", username)
            .put("password", password)
            .put("captchaKey", CAPTCHA_KEY)
            .put("captchaCode", "7")
            .toString();
    }
}
