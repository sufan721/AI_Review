package com.aireview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aireview.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SSO 注册策略约束端到端：
 * 关闭注册时 SSO 首次登录被拒；identifierFilter 不匹配拒绝；
 * 已绑定用户不受注册开关影响（登录仍可用）。
 */
class SsoPolicyTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
        jdbcTemplate.execute("TRUNCATE TABLE `sys_user_role`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_user_sso_binding`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_login_log`");
        jdbcTemplate.execute("TRUNCATE TABLE `identity_provider`");
        jdbcTemplate.execute("TRUNCATE TABLE `instance_setting`");
        jdbcTemplate.execute("TRUNCATE TABLE `user`");
    }

    @Test
    void ssoAutoSignupIsRejectedWhenRegistrationDisabled() throws Exception {
        jdbcTemplate.update("INSERT INTO instance_setting(name, value) VALUES "
            + "('GENERAL', '{\"disallowUserRegistration\":true,\"disallowPasswordAuth\":false}')");

        mockMvc.perform(post("/api/auth/sso/login")
                .contentType(APPLICATION_JSON)
                .content(ssoLogin("OIDC", "new-user-1", "newuser", "new@example.com")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("用户注册已关闭，请联系管理员建号或绑定账号"));

        // 没有建号
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void ssoAutoSignupStillWorksWhenRegistrationOpen() throws Exception {
        mockMvc.perform(post("/api/auth/sso/login")
                .contentType(APPLICATION_JSON)
                .content(ssoLogin("OIDC", "new-user-2", "sso_user", "sso@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.username").value("sso_user"));
    }

    @Test
    void boundUserCanStillLoginWhenRegistrationDisabled() throws Exception {
        // 开放注册时先建号绑定
        mockMvc.perform(post("/api/auth/sso/login")
                .contentType(APPLICATION_JSON)
                .content(ssoLogin("OIDC", "existing-user", "bound", "bound@example.com")))
            .andExpect(status().isOk());

        // 关闭注册后再登录：已绑定用户应仍可登录
        jdbcTemplate.update("UPDATE instance_setting SET value = "
            + "'{\"disallowUserRegistration\":true,\"disallowPasswordAuth\":false}' "
            + "WHERE name = 'GENERAL'");

        mockMvc.perform(post("/api/auth/sso/login")
                .contentType(APPLICATION_JSON)
                .content(ssoLogin("OIDC", "existing-user", "bound", "bound@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.username").value("bound"));
    }

    @Test
    void ssoSubjectNotMatchingIdentifierFilterIsRejected() throws Exception {
        // 登记 provider="OIDC" 的身份源，只允许 corp- 前缀的 subject
        jdbcTemplate.update("INSERT INTO identity_provider(uid, name, type, identifier_filter) VALUES "
            + "('OIDC', '公司 OIDC', 'OIDC', 'corp-.*')");

        // corp- 前缀：通过
        mockMvc.perform(post("/api/auth/sso/login")
                .contentType(APPLICATION_JSON)
                .content(ssoLogin("OIDC", "corp-alice", "alice", null)))
            .andExpect(status().isOk());

        // 非 corp- 前缀：拒绝
        mockMvc.perform(post("/api/auth/sso/login")
                .contentType(APPLICATION_JSON)
                .content(ssoLogin("OIDC", "outsider-bob", "bob", null)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("身份标识不符合该身份源的准入规则"));
    }

    @Test
    void providerWithoutRegisteredIdpSkipsFilter() throws Exception {
        // mock IdP 流程：provider 未登记 identity_provider，不触发过滤
        mockMvc.perform(post("/api/auth/sso/login")
                .contentType(APPLICATION_JSON)
                .content(ssoLogin("OIDC", "any-subject", "anyuser", null)))
            .andExpect(status().isOk());
    }

    private String ssoLogin(String provider, String subject, String username, String email) {
        ObjectNode node = objectMapper.createObjectNode()
            .put("provider", provider)
            .put("subject", subject)
            .put("username", username);
        if (email != null) {
            node.put("email", email);
        }
        return node.toString();
    }
}
