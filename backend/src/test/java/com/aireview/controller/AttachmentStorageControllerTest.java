package com.aireview.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aireview.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** 附件存储端到端：配置仅管理员、FS/DB 两种模式上传下载往返、切换后按行分派、删除。 */
class AttachmentStorageControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @TempDir private Path attachDir;

    @BeforeEach
    void resetData() {
        jdbcTemplate.execute("TRUNCATE TABLE attachment_blob");
        jdbcTemplate.execute("TRUNCATE TABLE attachment");
        jdbcTemplate.execute("TRUNCATE TABLE instance_setting");
        jdbcTemplate.execute("TRUNCATE TABLE sys_login_log");
        jdbcTemplate.execute("TRUNCATE TABLE sys_user_role");
        jdbcTemplate.execute("TRUNCATE TABLE `user`");
    }

    @Test
    void adminOnlySettingAndDefaultIsLocalFs() throws Exception {
        String admin = register("root");
        String outsider = register("bob");

        mockMvc.perform(get("/api/admin/attachment-storage/setting")
                .header("Authorization", "Bearer " + outsider))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/attachment-storage/status")
                .header("Authorization", "Bearer " + outsider))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/attachment-storage/setting")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mode").value("LOCAL_FS"));
    }

    @Test
    void rejectsBadSizeAndNonWritableDir() throws Exception {
        String admin = register("root");

        mockMvc.perform(put("/api/admin/attachment-storage/setting")
                .header("Authorization", "Bearer " + admin)
                .contentType(new MediaType(APPLICATION_JSON, StandardCharsets.UTF_8))
                .content(settingJson("LOCAL_FS", attachDir.toString(), 0)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("单附件大小上限需在 1-200 MB 之间"));

        // 已存在的普通文件当目录 → 不可写
        Path asFile = attachDir.resolve("not-a-dir");
        java.nio.file.Files.writeString(asFile, "x");
        mockMvc.perform(put("/api/admin/attachment-storage/setting")
                .header("Authorization", "Bearer " + admin)
                .contentType(new MediaType(APPLICATION_JSON, StandardCharsets.UTF_8))
                .content(settingJson("LOCAL_FS", asFile.toString(), 20)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("附件目录不可写")));
    }

    @Test
    void fsUploadDownloadAndDelete() throws Exception {
        String admin = register("root");
        saveMode(admin, "LOCAL_FS", attachDir.toString());

        long id = upload(admin, "note.txt", "hello fs".getBytes(StandardCharsets.UTF_8));
        assertEquals("LOCAL_FS", jdbcTemplate.queryForObject(
            "SELECT storage_mode FROM attachment WHERE id = ?", String.class, id));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM attachment_blob WHERE attachment_id = ?", Integer.class, id));

        assertArrayEquals("hello fs".getBytes(StandardCharsets.UTF_8), download(admin, id));

        mockMvc.perform(delete("/api/attachments/" + id)
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM attachment WHERE id = ?", Integer.class, id));
    }

    @Test
    void databaseUploadDownloadAndSwitchKeepsOldReadable() throws Exception {
        String admin = register("root");
        saveMode(admin, "DATABASE", attachDir.toString());

        byte[] payload = new byte[] {0, 1, 2, (byte) 0xFF, 10, 20};
        long dbId = upload(admin, "blob.bin", payload);
        assertEquals("DATABASE", jdbcTemplate.queryForObject(
            "SELECT storage_mode FROM attachment WHERE id = ?", String.class, dbId));
        assertArrayEquals(payload, download(admin, dbId));

        // 切回文件系统：新附件落文件，老的数据库附件仍按自己记录的方式可读
        saveMode(admin, "LOCAL_FS", attachDir.toString());
        long fsId = upload(admin, "later.txt", "later".getBytes(StandardCharsets.UTF_8));
        assertEquals("LOCAL_FS", jdbcTemplate.queryForObject(
            "SELECT storage_mode FROM attachment WHERE id = ?", String.class, fsId));
        assertArrayEquals(payload, download(admin, dbId), "切换配置后数据库附件应仍可读");
        assertArrayEquals("later".getBytes(StandardCharsets.UTF_8), download(admin, fsId));

        JsonNode status = objectMapper.readTree(mockMvc.perform(get("/api/admin/attachment-storage/status")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals(1, status.path("dbCount").asInt());
        assertEquals(1, status.path("fsCount").asInt());
    }

    @Test
    void nonOwnerCannotDownloadOrDelete() throws Exception {
        String alice = register("alice");
        String bob = register("bob"); // 第二个用户，非管理员
        saveMode(alice, "LOCAL_FS", attachDir.toString());

        long id = upload(alice, "private.txt", "secret".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/api/attachments/" + id + "/download")
                .header("Authorization", "Bearer " + bob))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/attachments/" + id)
                .header("Authorization", "Bearer " + bob))
            .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private String settingJson(String mode, String dir, int maxMb) {
        return "{\"mode\":\"" + mode + "\",\"localDir\":\""
            + dir.replace("\\", "\\\\") + "\",\"maxSizeMb\":" + maxMb + "}";
    }

    private void saveMode(String token, String mode, String dir) throws Exception {
        mockMvc.perform(put("/api/admin/attachment-storage/setting")
                .header("Authorization", "Bearer " + token)
                .contentType(new MediaType(APPLICATION_JSON, StandardCharsets.UTF_8))
                .content(settingJson(mode, dir, 20)))
            .andExpect(status().isOk());
    }

    private long upload(String token, String name, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "application/octet-stream", bytes);
        String body = mockMvc.perform(multipart("/api/attachments").file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = objectMapper.readTree(body).path("data").path("id").asLong();
        assertTrue(id > 0);
        return id;
    }

    private byte[] download(String token, long id) throws Exception {
        return mockMvc.perform(get("/api/attachments/" + id + "/download")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
    }

    private String register(String username) throws Exception {
        ObjectNode node = objectMapper.createObjectNode()
            .put("username", username).put("password", "Secret123!");
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/register").contentType(APPLICATION_JSON).content(node.toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }
}
