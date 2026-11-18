package com.aireview.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aireview.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

class DocumentControllerTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String PASSWORD = "Secret123!";

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE `document`");
        jdbcTemplate.execute("TRUNCATE TABLE `user`");
        aliceToken = register("alice");
        bobToken = register("bob");
    }

    @Test
    void uploadListAndDetailKeepMarkdownContent() throws Exception {
        String body = mockMvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", "guide.md", "text/markdown", "# Guide\n\ntext".getBytes()))
                .header("Authorization", bearer(aliceToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fileName").value("guide.md"))
            .andExpect(jsonPath("$.data.content").value("# Guide\n\ntext"))
            .andExpect(jsonPath("$.data.indexStatus").value("PENDING"))
            .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(get("/api/documents").header("Authorization", bearer(aliceToken)))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].fileName").value("guide.md"));
        mockMvc.perform(get("/api/documents/" + id).header("Authorization", bearer(aliceToken)))
            .andExpect(jsonPath("$.data.content").value("# Guide\n\ntext"));
    }

    @Test
    void uploadRejectsNonMarkdownAndEmptyFiles() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", "guide.txt", "text/plain", "text".getBytes()))
                .header("Authorization", bearer(aliceToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", "empty.md", "text/markdown", new byte[0]))
                .header("Authorization", bearer(aliceToken)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void documentsAreIsolatedByOwner() throws Exception {
        String body = mockMvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", "private.md", "text/markdown", "secret".getBytes()))
                .header("Authorization", bearer(bobToken)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();
        mockMvc.perform(get("/api/documents/" + id).header("Authorization", bearer(aliceToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/documents/" + id).header("Authorization", bearer(aliceToken)))
            .andExpect(status().isForbidden());
    }

    private String register(String username) throws Exception {
        String credentials = objectMapper.createObjectNode()
            .put("username", username)
            .put("password", PASSWORD)
            .toString();
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register")
                .contentType(APPLICATION_JSON).content(credentials))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
