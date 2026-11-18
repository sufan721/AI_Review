package com.aireview.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aireview.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PAT 端到端：创建 → 用 PAT 访问受保护接口 → 吊销 → 失效。
 */
class PersonalAccessTokenControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String PASSWORD = "Secret123!";

    private String jwtToken;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE `personal_access_token`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_user_role`");
        jdbcTemplate.execute("TRUNCATE TABLE `user`");
        // first_admin 占位 → ADMIN，确保 alice 是非首用户 → USER
        register("first_admin");
        jwtToken = register("alice");
    }

    @Test
    void patGrantsAccessToProtectedEndpointAndCanBeRevoked() throws Exception {
        // 1) 创建 PAT
        ObjectNode createBody = objectMapper.createObjectNode()
            .put("description", "for CI")
            .put("ttlDays", (Integer) null);
        String created = mockMvc.perform(post("/api/users/me/pats")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(APPLICATION_JSON)
                .content(createBody.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").value(org.hamcrest.Matchers.startsWith("memos_pat_")))
            .andReturn().getResponse().getContentAsString();
        String rawToken = objectMapper.readTree(created).path("data").path("token").asText();
        long patId = objectMapper.readTree(created).path("data").path("id").asLong();

        // 2) 用 PAT 访问受保护接口（/api/auth/me）应成功
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + rawToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("alice"));

        // 3) 列出 PAT，不含 token 哈希
        mockMvc.perform(get("/api/users/me/pats").header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].description").value("for CI"));

        // 4) 吊销后再用 PAT 访问应 401
        mockMvc.perform(delete("/api/users/me/pats/" + patId)
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + rawToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void patFromOtherUserCannotBeRevokedByMe() throws Exception {
        String bobToken = register("bob");

        ObjectNode createBody = objectMapper.createObjectNode().put("description", "bob's pat");
        String created = mockMvc.perform(post("/api/users/me/pats")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(APPLICATION_JSON)
                .content(createBody.toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long bobPatId = objectMapper.readTree(created).path("data").path("id").asLong();

        // alice 尝试吊销 bob 的 PAT：返回 200 但删除 0 行，bob 的 PAT 仍可用
        mockMvc.perform(delete("/api/users/me/pats/" + bobPatId)
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk());

        String bobRawToken = objectMapper.readTree(created).path("data").path("token").asText();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + bobRawToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("bob"));
    }

    private String register(String username) throws Exception {
        ObjectNode node = objectMapper.createObjectNode()
            .put("username", username)
            .put("password", PASSWORD);
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(node.toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        return data.path("accessToken").asText();
    }
}
