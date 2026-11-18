package com.aireview.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * 权限管理增强的端到端测试：
 * 首次注册引导状态、管理员改密码/改用户名、成员列表带角色、资料编辑开关。
 */
class AdminManagementTest extends AbstractIntegrationTest {

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
        // 只清 GENERAL，避免影响其他测试类预置的 ACCESS 等设置
        jdbcTemplate.execute("DELETE FROM `instance_setting` WHERE name = 'GENERAL'");
    }

    @Test
    void bootstrapReflectsWhetherInstanceHasUsers() throws Exception {
        // 空库：匿名可读，initialized = false
        mockMvc.perform(get("/api/instance/bootstrap"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.initialized").value(false));

        register("root");

        mockMvc.perform(get("/api/instance/bootstrap"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.initialized").value(true));
    }

    @Test
    void adminUsersEndpointIncludesRoleCodes() throws Exception {
        String adminToken = register("root");
        register("bob");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.username=='root')].roles[0]").value("ROLE_ADMIN"))
            .andExpect(jsonPath("$.data[?(@.username=='bob')].roles[0]").value("ROLE_USER"));
    }

    @Test
    void adminCanResetMemberPasswordAndInvalidateOldSession() throws Exception {
        String adminToken = register("root");
        String memberToken = register("bob");
        long bobId = userId("bob");

        ObjectNode body = objectMapper.createObjectNode().put("newPassword", "BrandNew789!");
        mockMvc.perform(put("/api/admin/users/" + bobId + "/password")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk());

        // token_version+1 后成员旧 JWT 失效
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isUnauthorized());

        // 旧密码登录失败，新密码登录成功
        seedCaptcha();
        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginBody("bob", PASSWORD)))
            .andExpect(status().isUnauthorized());
        seedCaptcha();
        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginBody("bob", "BrandNew789!")))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanRenameMemberWithDuplicateCheck() throws Exception {
        String adminToken = register("root");
        String memberToken = register("bob");
        register("carol");
        long bobId = userId("bob");

        // 改成已存在的用户名 → 409
        ObjectNode dup = objectMapper.createObjectNode().put("newUsername", "carol");
        mockMvc.perform(put("/api/admin/users/" + bobId + "/username")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(dup.toString()))
            .andExpect(status().isConflict());

        // 改成新用户名 → 成功，且旧会话失效
        ObjectNode rename = objectMapper.createObjectNode().put("newUsername", "bob_renamed");
        mockMvc.perform(put("/api/admin/users/" + bobId + "/username")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(rename.toString()))
            .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `user` WHERE username = 'bob_renamed'", Integer.class);
        assertEquals(1, count);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotOperateOnOwnAccount() throws Exception {
        String adminToken = register("root");
        register("bob");
        long rootId = userId("root");
        long bobId = userId("bob");
        // 制造第二个管理员，排除「最后一个 admin 保护」的干扰
        mockMvc.perform(post("/api/admin/users/" + bobId + "/roles/admin")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        // 自己给自己降权 → 403
        mockMvc.perform(delete("/api/admin/users/" + rootId + "/roles/admin")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("不能对自己降权，请由其他管理员操作"));

        // 自己禁用自己 → 403
        mockMvc.perform(put("/api/admin/users/" + rootId + "/status?status=0")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("不能对自己禁用，请由其他管理员操作"));

        // 自己删自己 → 403
        mockMvc.perform(delete("/api/admin/users/" + rootId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("不能对自己删除，请由其他管理员操作"));

        // 自己通过管理接口改密码 → 403（自助改密走 /api/auth/change-password）
        ObjectNode pwd = objectMapper.createObjectNode().put("newPassword", "Another123!");
        mockMvc.perform(put("/api/admin/users/" + rootId + "/password")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(pwd.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("不能对自己改密码，请前往个人资料页修改"));

        // 自己通过管理接口改用户名 → 403（自助改资料走 /api/users/me）
        ObjectNode uname = objectMapper.createObjectNode().put("newUsername", "root_new");
        mockMvc.perform(put("/api/admin/users/" + rootId + "/username")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(uname.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("不能对自己改用户名，请前往个人资料页修改"));
    }

    @Test
    void adminCanCreateMemberWithUserRole() throws Exception {
        String adminToken = register("root");

        // 管理员新建成员 → 返回带 ROLE_USER 的用户
        ObjectNode payload = objectMapper.createObjectNode()
            .put("username", "newmember")
            .put("password", PASSWORD);
        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("newmember"))
            .andExpect(jsonPath("$.data.roles", org.hamcrest.Matchers.hasItem("ROLE_USER")))
            .andExpect(jsonPath("$.data.roles", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("ROLE_ADMIN"))));

        // 新成员能用初始密码登录
        seedCaptcha();
        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginBody("newmember", PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // 重名创建 → 409
        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isConflict());

        // 普通用户无权限创建
        String memberToken = register("bob");
        ObjectNode other = objectMapper.createObjectNode()
            .put("username", "another").put("password", PASSWORD);
        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(APPLICATION_JSON)
                .content(other.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void profileEditSwitchesBlockUsernameAndNicknameOnly() throws Exception {
        String adminToken = register("root");
        String memberToken = register("bob");

        // admin 打开两个禁止开关
        mockMvc.perform(put("/api/instance/settings/general")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(generalBody(true, true).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.forbidChangeUsername").value(true))
            .andExpect(jsonPath("$.data.forbidChangeNickname").value(true));

        // 普通用户改昵称 → 403
        ObjectNode nick = objectMapper.createObjectNode().put("displayName", "新昵称");
        mockMvc.perform(patch("/api/users/me")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(APPLICATION_JSON)
                .content(nick.toString()))
            .andExpect(status().isForbidden());

        // 普通用户改用户名 → 403
        ObjectNode uname = objectMapper.createObjectNode().put("username", "bob_new");
        mockMvc.perform(patch("/api/users/me")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(APPLICATION_JSON)
                .content(uname.toString()))
            .andExpect(status().isForbidden());

        // 不受限的个人描述仍可修改
        ObjectNode desc = objectMapper.createObjectNode().put("description", "可以改");
        mockMvc.perform(patch("/api/users/me")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(APPLICATION_JSON)
                .content(desc.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.description").value("可以改"));

        // 关闭开关后，用户名与昵称恢复可改
        mockMvc.perform(put("/api/instance/settings/general")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(generalBody(false, false).toString()))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/users/me")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(APPLICATION_JSON)
                .content(nick.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.displayName").value("新昵称"));
    }

    private ObjectNode generalBody(boolean forbidUsername, boolean forbidNickname) {
        return objectMapper.createObjectNode()
            .put("disallowUserRegistration", false)
            .put("disallowPasswordAuth", false)
            .put("forbidChangeUsername", forbidUsername)
            .put("forbidChangeNickname", forbidNickname);
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
            "SELECT id FROM `user` WHERE username = ?", Long.class, username);
        return id == null ? -1 : id;
    }
}
