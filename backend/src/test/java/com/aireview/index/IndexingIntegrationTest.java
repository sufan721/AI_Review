package com.aireview.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aireview.support.AbstractIntegrationTest;
import com.aireview.support.ControlledTaskExecutor;
import com.aireview.support.FakeEmbeddingClient;
import com.aireview.support.InMemoryVectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class IndexingIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ControlledTaskExecutor taskExecutor;
    @Autowired private InMemoryVectorStore vectorStore;
    @Autowired private FakeEmbeddingClient embeddingClient;

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE `note`");
        jdbcTemplate.execute("TRUNCATE TABLE `document`");
        jdbcTemplate.execute("TRUNCATE TABLE `user`");
        aliceToken = register("alice");
        bobToken = register("bob");
    }

    @Test
    void createSchedulesAsyncIndexAndStaysPending() throws Exception {
        String id = createNote(aliceToken, "标题", "# 正文");

        assertThat(statusOf(id)).isEqualTo("PENDING");
        assertThat(taskExecutor.pendingCount()).isEqualTo(1);
        assertThat(chunkCount(id)).isZero();
    }

    @Test
    void drainIndexesBaselineToIndexed() throws Exception {
        String id = createNote(aliceToken, "标题", "# 一级\n正文\n## 二级\n更多");

        taskExecutor.drain();

        assertThat(statusOf(id)).isEqualTo("INDEXED");
        assertThat(chunkCount(id)).isGreaterThan(0);
        assertThat(vectorStore.recordsFor(ResourceType.NOTE, Long.parseLong(id))).isNotEmpty();
    }

    @Test
    void updateBumpsVersionAndReplacesVectors() throws Exception {
        String id = createNote(aliceToken, "标题", "# 旧内容");
        taskExecutor.drain();
        assertThat(statusOf(id)).isEqualTo("INDEXED");
        assertThat(versionOf(id)).isEqualTo(1);

        updateNote(aliceToken, id, "标题", "# 新内容");
        assertThat(versionOf(id)).isEqualTo(2);
        assertThat(statusOf(id)).isEqualTo("PENDING");
        taskExecutor.drain();

        assertThat(statusOf(id)).isEqualTo("INDEXED");
        assertThat(chunkVersions(id)).containsExactly(2);
        List<Integer> versions = vectorStore.recordsFor(ResourceType.NOTE, Long.parseLong(id)).stream()
            .map(VectorRecord::contentVersion).distinct().toList();
        assertThat(versions).containsExactly(2);
    }

    @Test
    void deleteRemovesChunksAndVectors() throws Exception {
        String id = createNote(aliceToken, "标题", "# 正文");
        taskExecutor.drain();

        mockMvc.perform(delete("/api/notes/" + id).header("Authorization", bearer(aliceToken)))
            .andExpect(status().isOk());

        assertThat(chunkCount(id)).isZero();
        assertThat(vectorStore.recordsFor(ResourceType.NOTE, Long.parseLong(id))).isEmpty();
    }

    @Test
    void failureRecordsReasonAndRetryRecovers() throws Exception {
        embeddingClient.setFail(true);
        String id = createNote(aliceToken, "标题", "# 正文");
        taskExecutor.drain();

        assertThat(statusOf(id)).isEqualTo("FAILED");
        assertThat(errorOf(id)).isNotBlank();
        assertThat(retryCountOf(id)).isEqualTo(1);

        embeddingClient.setFail(false);
        mockMvc.perform(post("/api/notes/" + id + "/reindex").header("Authorization", bearer(aliceToken)))
            .andExpect(status().isOk());
        taskExecutor.drain();

        assertThat(statusOf(id)).isEqualTo("INDEXED");
        assertThat(errorOf(id)).isNull();
        assertThat(retryCountOf(id)).isZero();
    }

    @Test
    void searchIsIsolatedByUser() throws Exception {
        String aliceId = createNote(aliceToken, "A", "# alice 正文");
        String bobId = createNote(bobToken, "B", "# bob 正文");
        taskExecutor.drain();

        long aliceUid = userId("alice");
        List<VectorHit> own = vectorStore.search(
            new VectorSearchFilter(aliceUid, ResourceType.NOTE, Long.parseLong(aliceId), 1, 10), new float[] {1f});
        assertThat(own).isNotEmpty();
        assertThat(own).allMatch(hit -> hit.resourceId() == Long.parseLong(aliceId));

        // 用 alice 的 userId 去检索 bob 的资源：因过滤条件同时限定 user_id 与 resource_id，命中为空
        List<VectorHit> cross = vectorStore.search(
            new VectorSearchFilter(aliceUid, ResourceType.NOTE, Long.parseLong(bobId), 1, 10), new float[] {1f});
        assertThat(cross).isEmpty();
    }

    private String createNote(String token, String title, String content) throws Exception {
        String body = mockMvc.perform(post("/api/notes")
                .header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.createObjectNode().put("title", title).put("content", content).toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private void updateNote(String token, String id, String title, String content) throws Exception {
        mockMvc.perform(put("/api/notes/" + id)
                .header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.createObjectNode().put("title", title).put("content", content).toString()))
            .andExpect(status().isOk());
    }

    private String register(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.createObjectNode().put("username", username).put("password", "secret123").toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long userId(String username) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM `user` WHERE username = ?", Long.class, username);
        return id == null ? 0L : id;
    }

    private String statusOf(String id) {
        return jdbcTemplate.queryForObject("SELECT index_status FROM `note` WHERE id = ?", String.class, Long.parseLong(id));
    }

    private int versionOf(String id) {
        Integer version = jdbcTemplate.queryForObject("SELECT content_version FROM `note` WHERE id = ?", Integer.class, Long.parseLong(id));
        return version == null ? 0 : version;
    }

    private String errorOf(String id) {
        return jdbcTemplate.queryForObject("SELECT index_error FROM `note` WHERE id = ?", String.class, Long.parseLong(id));
    }

    private int retryCountOf(String id) {
        Integer count = jdbcTemplate.queryForObject("SELECT retry_count FROM `note` WHERE id = ?", Integer.class, Long.parseLong(id));
        return count == null ? 0 : count;
    }

    private int chunkCount(String id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `document_chunk` WHERE resource_type = 'NOTE' AND resource_id = ?", Integer.class, Long.parseLong(id));
        return count == null ? 0 : count;
    }

    private List<Integer> chunkVersions(String id) {
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT content_version FROM `document_chunk` WHERE resource_type = 'NOTE' AND resource_id = ?",
            Integer.class, Long.parseLong(id));
    }
}
