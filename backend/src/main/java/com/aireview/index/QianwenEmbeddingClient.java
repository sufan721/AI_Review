package com.aireview.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 阿里云百炼（DashScope）千问文本嵌入客户端。
 *
 * <p>模型、地址与密钥均由配置注入；默认 {@code text-embedding-v4}。请求按 batch 一次提交全部切片。
 */
public class QianwenEmbeddingClient implements EmbeddingClient {
    private static final String EMBED_PATH = "/api/v1/services/embeddings/text-embedding/text-embedding";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public QianwenEmbeddingClient(String baseUrl, String apiKey, String model, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode textsNode = body.putObject("input").putArray("texts");
        texts.forEach(textsNode::add);
        body.putObject("parameters").put("text_type", "document");

        JsonNode response;
        try {
            String raw = restClient.post()
                .uri(EMBED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);
            response = objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw new IndexingException("嵌入模型调用失败: " + exception.getMessage(), exception);
        }

        JsonNode embeddings = response.path("output").path("embeddings");
        if (!embeddings.isArray()) {
            throw new IndexingException("嵌入模型响应缺少 output.embeddings");
        }
        float[][] vectors = new float[texts.size()][];
        for (JsonNode item : embeddings) {
            int index = item.path("text_index").asInt(-1);
            if (index < 0 || index >= vectors.length) {
                continue;
            }
            JsonNode vec = item.path("embedding");
            float[] vector = new float[vec.size()];
            for (int i = 0; i < vec.size(); i++) {
                vector[i] = (float) vec.get(i).asDouble();
            }
            vectors[index] = vector;
        }
        List<float[]> result = new ArrayList<>(texts.size());
        for (int i = 0; i < vectors.length; i++) {
            if (vectors[i] == null) {
                throw new IndexingException("嵌入模型响应缺失第 " + i + " 条向量");
            }
            result.add(vectors[i]);
        }
        return result;
    }
}
