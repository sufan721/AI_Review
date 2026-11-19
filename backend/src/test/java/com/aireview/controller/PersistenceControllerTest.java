package com.aireview.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aireview.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 数据持久化面板端到端：配置生效 → 备份 → 校验 → 删除数据 → 恢复 → 权限拦截。 */
@TestPropertySource(properties = {
    "ai-review.persistence.scheduler-enabled=false",
    "ai-review.persistence.redis-lock-enabled=false"
})
class PersistenceControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @TempDir private Path backupDir;

    @BeforeEach
    void resetData() {
        jdbcTemplate.execute("TRUNCATE TABLE backup_record");
        jdbcTemplate.execute("TRUNCATE TABLE note");
        jdbcTemplate.execute("TRUNCATE TABLE instance_setting");
        jdbcTemplate.execute("TRUNCATE TABLE user_setting");
        jdbcTemplate.execute("TRUNCATE TABLE sys_login_log");
        jdbcTemplate.execute("TRUNCATE TABLE sys_user_role");
        jdbcTemplate.execute("TRUNCATE TABLE `user`");
    }

    /** 归档目录尚未创建时，状态位不应误报「不可写」——保存配置与真正备份都会自动建目录。 */
    @Test
    void statusTreatsMissingDirectoryAsWritableAndCreatesIt() throws Exception {
        String admin = register("root");
        Path fresh = backupDir.resolve("nested-not-created-yet");

        saveSetting(admin, fresh.toString(), "0 0 3 * * *", false, 30);
        Files.deleteIfExists(fresh);
        assertFalse(Files.exists(fresh), "前置条件：目录已删除，模拟首次使用");

        mockMvc.perform(get("/api/admin/persistence/status").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.directoryWritable").value(true));
        assertTrue(Files.isDirectory(fresh), "状态查询应能按需创建归档目录");
    }

    @Test
    void backupVerifyAndRestoreRoundTrip() throws Exception {
        String admin = register("root");
        String title = "快照往返测试";
        String content = "# 正文\n\n恢复后应原样回来";
        createNote(admin, title, content);

        saveSetting(admin, backupDir.toString(), "0 0 3 * * *", false, 30);

        mockMvc.perform(get("/api/admin/persistence/status").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.directoryWritable").value(true))
            .andExpect(jsonPath("$.data.totalRows").isNumber());

        String startBody = mockMvc.perform(post("/api/admin/persistence/backups")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        long recordId = objectMapper.readTree(startBody).path("data").path("id").asLong();
        waitForTask(admin, recordId);

        mockMvc.perform(get("/api/admin/persistence/backups/" + recordId)
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.triggerType").value("MANUAL"));
        try (var files = Files.list(backupDir)) {
            assertEquals(1, files.count());
        }

        mockMvc.perform(post("/api/admin/persistence/backups/" + recordId + "/verify")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true));

        jdbcTemplate.update("DELETE FROM note");
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM note", Integer.class));

        String restoreBody = mockMvc.perform(post("/api/admin/persistence/backups/" + recordId + "/restore")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"confirm\":\"RESTORE\"}"))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(restoreBody).path("data").path("taskId").asLong();
        waitForTask(admin, taskId);

        JsonNode notes = objectMapper.readTree(mockMvc.perform(get("/api/notes")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals(1, notes.path("total").asLong());
        assertEquals(title, notes.path("records").get(0).path("title").asText());
        assertEquals(content, jdbcTemplate.queryForObject("SELECT content FROM note", String.class));

        // 恢复前必须自动生成一份 PRE_RESTORE 快照并长期保留
        mockMvc.perform(get("/api/admin/persistence/backups")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(2));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM backup_record WHERE trigger_type='PRE_RESTORE' AND keep_forever=1",
            Integer.class));
    }

    @Test
    void invalidSettingIsRejectedAndNonAdminIsForbidden() throws Exception {
        String admin = register("root");
        String outsider = register("bob");

        mockMvc.perform(get("/api/admin/persistence/status")
                .header("Authorization", "Bearer " + outsider))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/persistence/backups")
                .header("Authorization", "Bearer " + outsider))
            .andExpect(status().isForbidden());

        saveSetting(admin, backupDir.toString(), "0 0 3 * * *", false, 30);
        // 直接构造非法 cron 的 PUT 会被 400 拒绝
        ObjectNode invalid = objectMapper.createObjectNode()
            .put("enabled", true).put("storagePath", backupDir.toString())
            .put("autoBackup", true).put("backupCron", "bad cron").put("retentionDays", 30);
        mockMvc.perform(put("/api/admin/persistence/setting")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content(invalid.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("备份周期（cron）不合法"));

        // 已存在的普通文件不能当目录用，服务端应拒绝保存
        Path occupied = backupDir.resolve("occupied.txt");
        Files.writeString(occupied, "not a directory");
        ObjectNode badPath = objectMapper.createObjectNode()
            .put("enabled", true).put("storagePath", occupied.toString())
            .put("autoBackup", false).put("backupCron", "0 0 3 * * *").put("retentionDays", 30);
        mockMvc.perform(put("/api/admin/persistence/setting")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content(badPath.toString()))
            .andExpect(status().isBadRequest());

        // 恢复必须输入确认字面量
        mockMvc.perform(post("/api/admin/persistence/backups/1/restore")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content("{\"confirm\":\"yes\"}"))
            .andExpect(status().isBadRequest());
    }

    private void saveSetting(String token, String path, String cron, boolean autoBackup, int days) throws Exception {
        ObjectNode node = objectMapper.createObjectNode()
            .put("enabled", true).put("storagePath", path)
            .put("autoBackup", autoBackup).put("backupCron", cron).put("retentionDays", days);
        mockMvc.perform(put("/api/admin/persistence/setting")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content(node.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.retentionDays").value(days));
    }

    private void createNote(String token, String title, String content) throws Exception {
        ObjectNode node = objectMapper.createObjectNode().put("title", title).put("content", content);
        mockMvc.perform(post("/api/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(new MediaType(APPLICATION_JSON, StandardCharsets.UTF_8))
                .content(node.toString()))
            .andExpect(status().isOk());
    }

    private void waitForTask(String token, long taskId) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            JsonNode node = objectMapper.readTree(mockMvc.perform(get("/api/admin/persistence/tasks/" + taskId)
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
            String status = node.path("status").asText();
            if ("SUCCESS".equals(status)) {
                return;
            }
            if ("FAILED".equals(status)) {
                throw new AssertionError("任务失败：" + node.path("message").asText());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("任务超时");
    }

    private String register(String username) throws Exception {
        ObjectNode node = objectMapper.createObjectNode()
            .put("username", username).put("password", "Secret123!");
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON).content(node.toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }
}
