package com.aireview.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * 管理员用户管理 + 改密码 + 会话版本号端到端：
 * 首用户自动 ADMIN、初始管理员不可被其他管理员删除/降权/禁用、改密码后旧 JWT 失效。
 */
class AdminUserControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuthStateStore store;

    private static final String PASSWORD = "Secret123!";
    private static final String CAPTCHA_KEY = "test-captcha";

    @BeforeEach
    void resetData() {
        jdbcTemplate.execute("TRUNCATE TABLE `sys_user_role`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_user_sso_binding`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_login_log`");
        jdbcTemplate.execute("TRUNCATE TABLE `user`");
    }

    @Test
    void firstRegisteredUserBecomesAdmin() throws Exception {
        String token = register("root");
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[?(@.username=='root')].initialAdmin").value(true));
    }

    @Test
    void initialAdminCannotBeRemovedOrModifiedByAnotherAdmin() throws Exception {
        String rootToken = register("root");
        String bobToken = register("bob");
        String carolToken = register("carol");
        long rootId = userId("root");
        long bobId = userId("bob");

        // 普通用户（未提权）不能调管理接口
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + carolToken))
            .andExpect(status().isForbidden());

        // 把 bob 提权成第二个管理员：此后 bob 也不能动初始管理员 root
        mockMvc.perform(post("/api/admin/users/" + bobId + "/roles/admin")
                .header("Authorization", "Bearer " + rootToken))
            .andExpect(status().isOk());

        // bob 撤销 root（初始管理员）的权限 → 403
        mockMvc.perform(delete("/api/admin/users/" + rootId + "/roles/admin")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(
                "初始管理员不可撤销管理员权限，该账号是实例的原始管理员，仅本人可维护自己的资料与密码"));

        // bob 删除 root → 403
        mockMvc.perform(delete("/api/admin/users/" + rootId)
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isForbidden());

        // bob 禁用 root → 403
        mockMvc.perform(put("/api/admin/users/" + rootId + "/status?status=0")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isForbidden());

        // root 仍是启用状态的管理员
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + rootToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.username=='root')].status").value(1))
            .andExpect(jsonPath("$.data[?(@.username=='root')].initialAdmin").value(true))
            .andExpect(jsonPath("$.data[?(@.username=='bob')].initialAdmin").value(false));

        // 非初始管理员（bob）可以被初始管理员撤销
        mockMvc.perform(delete("/api/admin/users/" + bobId + "/roles/admin")
                .header("Authorization", "Bearer " + rootToken))
            .andExpect(status().isOk());
    }

    @Test
    void changePasswordInvalidatesOldAccessToken() throws Exception {
        String oldToken = register("carol");

        // 改密码前旧 token 可用
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
            .andExpect(status().isOk());

        ObjectNode body = objectMapper.createObjectNode()
            .put("oldPassword", PASSWORD)
            .put("newPassword", "NewSecret456!");
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer " + oldToken)
                .contentType(APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk());

        // token_version+1 后旧 token 失效 → 401
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
            .andExpect(status().isUnauthorized());

        // 旧密码不再可登录，新密码可登录（登录需先 seed 验证码）
        seedCaptcha();
        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginBody("carol", PASSWORD)))
            .andExpect(status().isUnauthorized());
        seedCaptcha();
        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginBody("carol", "NewSecret456!")))
            .andExpect(status().isOk());
    }

    @Test
    void changePasswordRejectsWrongOldPassword() throws Exception {
        String token = register("dave");
        ObjectNode body = objectMapper.createObjectNode()
            .put("oldPassword", "wrong-password")
            .put("newPassword", "NewSecret456!");
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserAccessIsRejectedEvenWithFreshToken() throws Exception {
        String adminToken = register("root");
        String userToken = register("erin");
        long erinId = userId("erin");

        // 禁用 erin（非 admin，无 last-admin 保护触发）后她的旧 token 失效
        mockMvc.perform(put("/api/admin/users/" + erinId + "/status?status=0")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken))
            .andExpect(status().isUnauthorized());
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

    private void seedCaptcha() {
        store.put("captcha:" + CAPTCHA_KEY, "7", Duration.ofMinutes(5));
    }

    private String loginBody(String username, String password) {
        return objectMapper.createObjectNode()
            .put("username", username)
            .put("password", password)
            .put("captchaKey", CAPTCHA_KEY)
            .put("captchaCode", "7")
            .toString();
    }

    private long userId(String username) {
        Long id = jdbcTemplate.queryForObject(
            "SELECT id FROM `user` WHERE username = '" + username + "'", Long.class);
        return id == null ? -1 : id;
    }
}
