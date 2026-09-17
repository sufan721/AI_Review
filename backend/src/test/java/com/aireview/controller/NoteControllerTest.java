package com.aireview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

class NoteControllerTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String aliceToken;

    private String bobToken;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE `note`");
        jdbcTemplate.execute("TRUNCATE TABLE `user`");
        aliceToken = register("alice");
        bobToken = register("bob");
    }

    @Test
    void createReturnsPersistedNoteWithDefaults() throws Exception {
        mockMvc.perform(post("/api/notes")
                .header("Authorization", bearer(aliceToken))
                .contentType(APPLICATION_JSON)
                .content(noteBody("  周会纪要  ", "# 结论\n\n- 继续推进")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.title").value("周会纪要"))
            .andExpect(jsonPath("$.data.content").value("# 结论\n\n- 继续推进"))
            .andExpect(jsonPath("$.data.isPinned").value(false))
            .andExpect(jsonPath("$.data.isArchived").value(false))
            .andExpect(jsonPath("$.data.contentVersion").value(1))
            .andExpect(jsonPath("$.data.indexStatus").value("PENDING"))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
    }

    @Test
    void listReturnsOnlyOwnNotesAndOmitsContent() throws Exception {
        createNote(aliceToken, "A1", "alice 正文");
        createNote(aliceToken, "A2", "alice 正文");
        createNote(bobToken, "B1", "bob 正文");

        String body = mockMvc.perform(get("/api/notes").header("Authorization", bearer(aliceToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.records.length()").value(2))
            .andExpect(jsonPath("$.data.records[0].title").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("alice 正文");
        assertThat(objectMapper.readTree(body).path("data").path("records").get(0).has("content")).isFalse();
    }

    @Test
    void listOrdersPinnedFirst() throws Exception {
        createNote(aliceToken, "普通笔记", "x");
        String pinned = createNote(aliceToken, "置顶笔记", "x").path("id").asText();

        mockMvc.perform(patch("/api/notes/" + pinned + "/pin").header("Authorization", bearer(aliceToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isPinned").value(true));

        mockMvc.perform(get("/api/notes").header("Authorization", bearer(aliceToken)))
            .andExpect(jsonPath("$.data.records[0].title").value("置顶笔记"));
    }

    @Test
    void listFiltersByArchivedFlag() throws Exception {
        createNote(aliceToken, "活跃笔记", "x");
        String archived = createNote(aliceToken, "归档笔记", "x").path("id").asText();

        mockMvc.perform(patch("/api/notes/" + archived + "/archive").header("Authorization", bearer(aliceToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isArchived").value(true));

        mockMvc.perform(get("/api/notes?isArchived=false").header("Authorization", bearer(aliceToken)))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].title").value("活跃笔记"));

        mockMvc.perform(get("/api/notes?isArchived=true").header("Authorization", bearer(aliceToken)))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].title").value("归档笔记"));
    }

    @Test
    void listPaginates() throws Exception {
        for (int index = 0; index < 3; index++) {
            createNote(aliceToken, "笔记" + index, "x");
        }

        mockMvc.perform(get("/api/notes?page=2&size=2").header("Authorization", bearer(aliceToken)))
            .andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.size").value(2))
            .andExpect(jsonPath("$.data.records.length()").value(1));
    }

    @Test
    void invalidPaginationIsRejected() throws Exception {
        mockMvc.perform(get("/api/notes?page=0").header("Authorization", bearer(aliceToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/notes?size=1000").header("Authorization", bearer(aliceToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void detailReturnsTheRequestedNote() throws Exception {
        String id = createNote(aliceToken, "标题", "正文").path("id").asText();

        mockMvc.perform(get("/api/notes/" + id).header("Authorization", bearer(aliceToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("标题"))
            .andExpect(jsonPath("$.data.content").value("正文"));
    }

    @Test
    void updatePersistsTitleAndContentAndBumpsVersion() throws Exception {
        String id = createNote(aliceToken, "旧标题", "旧正文").path("id").asText();

        mockMvc.perform(put("/api/notes/" + id)
                .header("Authorization", bearer(aliceToken))
                .contentType(APPLICATION_JSON)
                .content(noteBody("新标题", "新正文")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("新标题"))
            .andExpect(jsonPath("$.data.content").value("新正文"))
            .andExpect(jsonPath("$.data.contentVersion").value(2));

        mockMvc.perform(get("/api/notes/" + id).header("Authorization", bearer(aliceToken)))
            .andExpect(jsonPath("$.data.content").value("新正文"))
            .andExpect(jsonPath("$.data.contentVersion").value(2));
    }

    @Test
    void updateWithoutContentChangeKeepsTheVersion() throws Exception {
        String id = createNote(aliceToken, "旧标题", "正文").path("id").asText();

        mockMvc.perform(put("/api/notes/" + id)
                .header("Authorization", bearer(aliceToken))
                .contentType(APPLICATION_JSON)
                .content(noteBody("只改标题", "正文")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("只改标题"))
            .andExpect(jsonPath("$.data.contentVersion").value(1));
    }

    @Test
    void mappingMarkdownContentIsStoredVerbatim() throws Exception {
        String markdown = "<script>alert(1)</script>\n\n| a | b |\n| - | - |\n| 1 | 2 |";

        String id = createNote(aliceToken, "含标记", markdown).path("id").asText();

        mockMvc.perform(get("/api/notes/" + id).header("Authorization", bearer(aliceToken)))
            .andExpect(jsonPath("$.data.content").value(markdown));
    }

    @Test
    void deleteRemovesTheNote() throws Exception {
        String id = createNote(aliceToken, "待删除", "x").path("id").asText();

        mockMvc.perform(delete("/api/notes/" + id).header("Authorization", bearer(aliceToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        assertThat(countNotes()).isZero();

        mockMvc.perform(get("/api/notes/" + id).header("Authorization", bearer(aliceToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void otherUsersNoteIsForbiddenAndIndistinguishableFromMissing() throws Exception {
        String bobNote = createNote(bobToken, "bob 的笔记", "机密内容").path("id").asText();

        mockMvc.perform(get("/api/notes/" + bobNote).header("Authorization", bearer(aliceToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/notes/999999").header("Authorization", bearer(aliceToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(put("/api/notes/" + bobNote)
                .header("Authorization", bearer(aliceToken))
                .contentType(APPLICATION_JSON)
                .content(noteBody("篡改", "篡改")))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/notes/" + bobNote).header("Authorization", bearer(aliceToken)))
            .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/notes/" + bobNote + "/pin").header("Authorization", bearer(aliceToken)))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/notes/" + bobNote).header("Authorization", bearer(bobToken)))
            .andExpect(jsonPath("$.data.content").value("机密内容"));
    }

    @Test
    void noteEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/notes"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/notes").contentType(APPLICATION_JSON).content(noteBody("t", "c")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void oversizedTitleIsRejected() throws Exception {
        mockMvc.perform(post("/api/notes")
                .header("Authorization", bearer(aliceToken))
                .contentType(APPLICATION_JSON)
                .content(noteBody("标".repeat(256), "正文")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    private JsonNode createNote(String token, String title, String content) throws Exception {
        String body = mockMvc.perform(post("/api/notes")
                .header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON)
                .content(noteBody(title, content)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private long countNotes() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `note`", Long.class);
        return count == null ? 0L : count;
    }

    private String register(String username) throws Exception {
        ObjectNode credentials = objectMapper.createObjectNode()
            .put("username", username)
            .put("password", "secret123");
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(credentials.toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    private String noteBody(String title, String content) {
        return objectMapper.createObjectNode()
            .put("title", title)
            .put("content", content)
            .toString();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
