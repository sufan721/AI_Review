package com.aireview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aireview.security.AuthStateStore;
import com.aireview.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class AuthControllerTest extends AbstractIntegrationTest {
    private static final String CAPTCHA_KEY = "test-captcha";
    private static final String PASSWORD = "Secret123!";
    private static final String SMS_CODE = "123456";
    private int phoneSeq = 0;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthStateStore store;

    @BeforeEach
    void clearUsers() {
        jdbcTemplate.execute("TRUNCATE TABLE `sys_user_role`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_user_sso_binding`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_login_log`");
        jdbcTemplate.execute("TRUNCATE TABLE `user`");
    }

    @Test
    void captchaReturnsKeyAndImage() throws Exception {
        mockMvc.perform(get("/api/auth/captcha"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.captchaKey").isNotEmpty())
            .andExpect(jsonPath("$.data.captchaImage").isString());
    }

    @Test
    void registerReturnsTokenAndStoresBcryptHash() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(registerCredentials("alice", PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.user.username").value("alice"))
            .andExpect(jsonPath("$.data.user.nickname").value("alice"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(PASSWORD).doesNotContain("passwordHash");

        String storedHash = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM `user` WHERE username = 'alice'", String.class);
        assertThat(storedHash).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void registerRejectsWeakPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(registerCredentials("alice", "secret123")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        register("alice");

        mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(registerCredentials("alice", PASSWORD)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void invalidRegistrationInputIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(registerCredentials("ab", PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void loginAcceptsTheStoredPassword() throws Exception {
        register("alice");

        seedCaptcha();
        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginCredentials("alice", PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.user.username").value("alice"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void loginRejectsWrongPasswordWithoutRevealingWhichFieldFailed() throws Exception {
        register("alice");

        seedCaptcha();
        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginCredentials("alice", "wrong-password")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        seedCaptcha();
        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginCredentials("nobody", PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void loginRejectsWrongCaptcha() throws Exception {
        register("alice");

        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginCredentials("alice", PASSWORD, "999")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void protectedEndpointRequiresAValidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void meReturnsTheTokenOwner() throws Exception {
        String token = register("alice");
        register("bob");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("alice"));
    }

    private String register(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(registerCredentials(username, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private void seedCaptcha() {
        store.put("captcha:" + CAPTCHA_KEY, "7", Duration.ofMinutes(5));
    }

    private String registerCredentials(String username, String password) {
        String phone = String.format("138%08d", ++phoneSeq);
        store.put("sms:code:" + phone, SMS_CODE, Duration.ofMinutes(5));
        return objectMapper.createObjectNode()
            .put("username", username)
            .put("password", password)
            .put("confirmPassword", password)
            .put("phone", phone)
            .put("smsCode", SMS_CODE)
            .toString();
    }

    private String loginCredentials(String username, String password) {
        return loginCredentials(username, password, "7");
    }

    private String loginCredentials(String username, String password, String captchaCode) {
        return objectMapper.createObjectNode()
            .put("username", username)
            .put("password", password)
            .put("captchaKey", CAPTCHA_KEY)
            .put("captchaCode", captchaCode)
            .toString();
    }
}
