package com.aireview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aireview.security.AuthStateStore;
import com.aireview.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

class KnowledgeBaseControllerTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AuthStateStore store;

    private static final String PASSWORD = "Secret123!";
    private static final String SMS_CODE = "123456";
    private int phoneSeq = 0;

    @BeforeEach
    void resetData() {
        jdbcTemplate.execute("TRUNCATE TABLE `knowledge_base_resource`");
        jdbcTemplate.execute("TRUNCATE TABLE `knowledge_base_member`");
        jdbcTemplate.execute("TRUNCATE TABLE `knowledge_base`");
        jdbcTemplate.execute("TRUNCATE TABLE `note`");
        jdbcTemplate.execute("TRUNCATE TABLE `document`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_user_role`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_user_sso_binding`");
        jdbcTemplate.execute("TRUNCATE TABLE `sys_login_log`");
        jdbcTemplate.execute("TRUNCATE TABLE `user`");
    }

    @Test
    void createKnowledgeBaseMakesCreatorOwner() throws Exception {
        String token = register("alice");
        long aliceId = userId("alice");

        String body = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON)
                .content(kbBody("我的知识库", "描述")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ownerId").value(aliceId))
            .andExpect(jsonPath("$.data.name").value("我的知识库"))
            .andExpect(jsonPath("$.data.memberRole").value("OWNER"))
            .andReturn().getResponse().getContentAsString();
        long kbId = objectMapper.readTree(body).path("data").path("id").asLong();

        assertThat(memberRole(kbId, aliceId)).isEqualTo("OWNER");
    }

    @Test
    void listReturnsOnlyMembersKnowledgeBases() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        String dave = register("dave");
        createKb(alice, "alice 的知识库");
        createKb(bob, "bob 的知识库");

        mockMvc.perform(get("/api/knowledge-bases").header("Authorization", bearer(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].name").value("alice 的知识库"))
            .andExpect(jsonPath("$.data[0].memberRole").value("OWNER"));

        mockMvc.perform(get("/api/knowledge-bases").header("Authorization", bearer(dave)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void detailRequiresMembership() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        long kbId = createKb(alice, "kb");

        mockMvc.perform(get("/api/knowledge-bases/" + kbId).header("Authorization", bearer(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberRole").value("OWNER"));

        mockMvc.perform(get("/api/knowledge-bases/" + kbId).header("Authorization", bearer(bob)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void updateRequiresOwner() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        long kbId = createKb(alice, "kb");
        addMember(alice, kbId, userId("bob"), "EDITOR");

        mockMvc.perform(put("/api/knowledge-bases/" + kbId)
                .header("Authorization", bearer(alice))
                .contentType(APPLICATION_JSON)
                .content(kbBody("新名字", null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("新名字"));

        mockMvc.perform(put("/api/knowledge-bases/" + kbId)
                .header("Authorization", bearer(bob))
                .contentType(APPLICATION_JSON)
                .content(kbBody("篡改", null)))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteRequiresOwnerAndKeepsResources() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        long kbId = createKb(alice, "kb");
        addMember(alice, kbId, userId("bob"), "EDITOR");
        long noteId = createNote(alice, "笔记", "内容");
        addResource(alice, kbId, "NOTE", noteId);

        mockMvc.perform(delete("/api/knowledge-bases/" + kbId).header("Authorization", bearer(bob)))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/knowledge-bases/" + kbId).header("Authorization", bearer(alice)))
            .andExpect(status().isOk());

        assertThat(count("knowledge_base", "id = " + kbId)).isZero();
        assertThat(count("knowledge_base_member", "knowledge_base_id = " + kbId)).isZero();
        assertThat(count("knowledge_base_resource", "knowledge_base_id = " + kbId)).isZero();
        assertThat(noteExists(noteId)).isTrue();
    }

    @Test
    void addMemberRequiresOwnerAndRejectsDuplicatesAndUnknownUsers() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        String dave = register("dave");
        long kbId = createKb(alice, "kb");
        long bobId = userId("bob");
        long daveId = userId("dave");

        // 非成员不能添加
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/members")
                .header("Authorization", bearer(bob))
                .contentType(APPLICATION_JSON)
                .content(memberBody(daveId, "VIEWER")))
            .andExpect(status().isForbidden());

        // OWNER 添加 EDITOR 成功
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/members")
                .header("Authorization", bearer(alice))
                .contentType(APPLICATION_JSON)
                .content(memberBody(bobId, "EDITOR")))
            .andExpect(status().isOk());
        assertThat(memberRole(kbId, bobId)).isEqualTo("EDITOR");

        // EDITOR 不能添加成员
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/members")
                .header("Authorization", bearer(bob))
                .contentType(APPLICATION_JSON)
                .content(memberBody(daveId, "VIEWER")))
            .andExpect(status().isForbidden());

        // 重复添加 → 400
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/members")
                .header("Authorization", bearer(alice))
                .contentType(APPLICATION_JSON)
                .content(memberBody(bobId, "VIEWER")))
            .andExpect(status().isBadRequest());

        // 不存在的用户 → 400
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/members")
                .header("Authorization", bearer(alice))
                .contentType(APPLICATION_JSON)
                .content(memberBody(999999L, "VIEWER")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateMemberRoleRequiresOwner() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        long kbId = createKb(alice, "kb");
        long bobId = userId("bob");
        addMember(alice, kbId, bobId, "EDITOR");

        mockMvc.perform(put("/api/knowledge-bases/" + kbId + "/members/" + bobId)
                .header("Authorization", bearer(bob))
                .contentType(APPLICATION_JSON)
                .content(roleBody("OWNER")))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/knowledge-bases/" + kbId + "/members/" + bobId)
                .header("Authorization", bearer(alice))
                .contentType(APPLICATION_JSON)
                .content(roleBody("VIEWER")))
            .andExpect(status().isOk());
        assertThat(memberRole(kbId, bobId)).isEqualTo("VIEWER");
    }

    @Test
    void removeMemberRequiresOwner() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        long kbId = createKb(alice, "kb");
        long bobId = userId("bob");
        addMember(alice, kbId, bobId, "EDITOR");

        mockMvc.perform(delete("/api/knowledge-bases/" + kbId + "/members/" + bobId)
                .header("Authorization", bearer(bob)))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/knowledge-bases/" + kbId + "/members/" + bobId)
                .header("Authorization", bearer(alice)))
            .andExpect(status().isOk());
        assertThat(count("knowledge_base_member", "knowledge_base_id = " + kbId + " AND user_id = " + bobId)).isZero();
    }

    @Test
    void cannotRemoveLastOwner() throws Exception {
        String alice = register("alice");
        long kbId = createKb(alice, "kb");
        long aliceId = userId("alice");

        mockMvc.perform(delete("/api/knowledge-bases/" + kbId + "/members/" + aliceId)
                .header("Authorization", bearer(alice)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void cannotDowngradeLastOwner() throws Exception {
        String alice = register("alice");
        long kbId = createKb(alice, "kb");
        long aliceId = userId("alice");

        mockMvc.perform(put("/api/knowledge-bases/" + kbId + "/members/" + aliceId)
                .header("Authorization", bearer(alice))
                .contentType(APPLICATION_JSON)
                .content(roleBody("EDITOR")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void addResourceRequiresEditorOrOwnerAndOwnership() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        String carol = register("carol");
        String dave = register("dave");
        long kbId = createKb(alice, "kb");
        addMember(alice, kbId, userId("bob"), "EDITOR");
        addMember(alice, kbId, userId("carol"), "VIEWER");

        long aliceNote = createNote(alice, "alice 笔记", "x");
        long bobNote = createNote(bob, "bob 笔记", "x");
        long carolNote = createNote(carol, "carol 笔记", "x");
        long daveNote = createNote(dave, "dave 笔记", "x");

        // OWNER 加自己的笔记 → 200
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/resources")
                .header("Authorization", bearer(alice))
                .contentType(APPLICATION_JSON)
                .content(resourceBody("NOTE", aliceNote)))
            .andExpect(status().isOk());
        // EDITOR 加自己的笔记 → 200
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/resources")
                .header("Authorization", bearer(bob))
                .contentType(APPLICATION_JSON)
                .content(resourceBody("NOTE", bobNote)))
            .andExpect(status().isOk());
        // EDITOR 加别人的笔记 → 403
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/resources")
                .header("Authorization", bearer(bob))
                .contentType(APPLICATION_JSON)
                .content(resourceBody("NOTE", aliceNote)))
            .andExpect(status().isForbidden());
        // VIEWER → 403
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/resources")
                .header("Authorization", bearer(carol))
                .contentType(APPLICATION_JSON)
                .content(resourceBody("NOTE", carolNote)))
            .andExpect(status().isForbidden());
        // 非成员 → 403
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/resources")
                .header("Authorization", bearer(dave))
                .contentType(APPLICATION_JSON)
                .content(resourceBody("NOTE", daveNote)))
            .andExpect(status().isForbidden());
        // 重复添加 → 400
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/resources")
                .header("Authorization", bearer(alice))
                .contentType(APPLICATION_JSON)
                .content(resourceBody("NOTE", aliceNote)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void removeResourceRequiresEditorOrOwner() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        String carol = register("carol");
        long kbId = createKb(alice, "kb");
        addMember(alice, kbId, userId("bob"), "EDITOR");
        addMember(alice, kbId, userId("carol"), "VIEWER");
        long noteId = createNote(alice, "笔记", "x");
        addResource(alice, kbId, "NOTE", noteId);

        mockMvc.perform(delete("/api/knowledge-bases/" + kbId + "/resources/NOTE/" + noteId)
                .header("Authorization", bearer(carol)))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/knowledge-bases/" + kbId + "/resources/NOTE/" + noteId)
                .header("Authorization", bearer(bob)))
            .andExpect(status().isOk());
        assertThat(count("knowledge_base_resource",
            "knowledge_base_id = " + kbId + " AND resource_type = 'NOTE' AND resource_id = " + noteId)).isZero();
    }

    @Test
    void listResourcesRequiresMembershipAndReturnsNames() throws Exception {
        String alice = register("alice");
        String dave = register("dave");
        long kbId = createKb(alice, "kb");
        long noteId = createNote(alice, "mynote", "content");
        long docId = uploadDocument(alice, "guide.md");
        addResource(alice, kbId, "NOTE", noteId);
        addResource(alice, kbId, "DOCUMENT", docId);

        mockMvc.perform(get("/api/knowledge-bases/" + kbId + "/resources").header("Authorization", bearer(dave)))
            .andExpect(status().isForbidden());

        String body = mockMvc.perform(get("/api/knowledge-bases/" + kbId + "/resources")
                .header("Authorization", bearer(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("mynote").contains("guide.md");
    }

    @Test
    void deletingNoteCleansUpResourceAssociations() throws Exception {
        String alice = register("alice");
        long kbId = createKb(alice, "kb");
        long noteId = createNote(alice, "待删除", "x");
        addResource(alice, kbId, "NOTE", noteId);

        assertThat(count("knowledge_base_resource", "resource_type = 'NOTE' AND resource_id = " + noteId)).isEqualTo(1);

        mockMvc.perform(delete("/api/notes/" + noteId).header("Authorization", bearer(alice)))
            .andExpect(status().isOk());

        assertThat(count("knowledge_base_resource", "resource_type = 'NOTE' AND resource_id = " + noteId)).isZero();
    }

    @Test
    void knowledgeBaseEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/knowledge-bases"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/knowledge-bases")
                .contentType(APPLICATION_JSON)
                .content(kbBody("x", null)))
            .andExpect(status().isUnauthorized());
    }

    private String register(String username) throws Exception {
        String phone = String.format("138%08d", ++phoneSeq);
        store.put("sms:code:" + phone, SMS_CODE, Duration.ofMinutes(5));
        ObjectNode credentials = objectMapper.createObjectNode()
            .put("username", username)
            .put("password", PASSWORD)
            .put("confirmPassword", PASSWORD)
            .put("phone", phone)
            .put("smsCode", SMS_CODE);
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(credentials.toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private long createKb(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON)
                .content(kbBody(name, null)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void addMember(String token, long kbId, long userId, String role) throws Exception {
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/members")
                .header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON)
                .content(memberBody(userId, role)))
            .andExpect(status().isOk());
    }

    private void addResource(String token, long kbId, String type, long resourceId) throws Exception {
        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/resources")
                .header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON)
                .content(resourceBody(type, resourceId)))
            .andExpect(status().isOk());
    }

    private long createNote(String token, String title, String content) throws Exception {
        String body = mockMvc.perform(post("/api/notes")
                .header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.createObjectNode().put("title", title).put("content", content).toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long uploadDocument(String token, String fileName) throws Exception {
        String body = mockMvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", fileName, "text/markdown",
                    "# 内容".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long userId(String username) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM `user` WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private String memberRole(long kbId, long userId) {
        return jdbcTemplate.queryForObject(
            "SELECT member_role FROM knowledge_base_member WHERE knowledge_base_id = ? AND user_id = ?",
            String.class, kbId, userId);
    }

    private long count(String table, String where) {
        String sql = "SELECT COUNT(*) FROM `" + table + "`";
        if (where != null && !where.isBlank()) {
            sql += " WHERE " + where;
        }
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private boolean noteExists(long id) {
        return count("note", "id = " + id) > 0;
    }

    private String kbBody(String name, String description) {
        ObjectNode node = objectMapper.createObjectNode().put("name", name);
        if (description != null) {
            node.put("description", description);
        }
        return node.toString();
    }

    private String memberBody(long userId, String role) {
        return objectMapper.createObjectNode().put("userId", userId).put("memberRole", role).toString();
    }

    private String roleBody(String role) {
        return objectMapper.createObjectNode().put("memberRole", role).toString();
    }

    private String resourceBody(String type, long resourceId) {
        return objectMapper.createObjectNode().put("resourceType", type).put("resourceId", resourceId).toString();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
