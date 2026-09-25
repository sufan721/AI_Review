package com.aireview.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 索引流水线装配。地址、集合、模型与超时均通过环境变量（带本地默认值）注入，
 * 不在代码中写死。
 */
@Configuration
public class IndexConfig {
    @Bean
    public MarkdownChunker markdownChunker(
        @Value("${ai-review.index.chunk.max-size:800}") int maxSize,
        @Value("${ai-review.index.chunk.overlap:100}") int overlap) {
        return new MarkdownChunker(maxSize, overlap);
    }

    @Bean
    public IndexTaskSink indexTaskSink() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1024);
        executor.setThreadNamePrefix("index-");
        executor.initialize();
        return executor::execute;
    }

    @Bean
    public EmbeddingClient embeddingClient(
        @Value("${ai-review.index.embedding.base-url:https://dashscope.aliyuncs.com}") String baseUrl,
        @Value("${ai-review.index.embedding.api-key:}") String apiKey,
        @Value("${ai-review.index.embedding.model:text-embedding-v4}") String model,
        ObjectMapper objectMapper) {
        return new QianwenEmbeddingClient(baseUrl, apiKey, model, objectMapper);
    }

    @Bean
    public VectorStore vectorStore(
        @Value("${ai-review.index.milvus.host:localhost}") String host,
        @Value("${ai-review.index.milvus.port:19530}") int port,
        @Value("${ai-review.index.milvus.collection:memo_chunks}") String collection,
        @Value("${ai-review.index.milvus.dimension:1024}") int dimension,
        @Value("${ai-review.index.milvus.timeout-ms:10000}") long timeoutMs) {
        return new MilvusVectorStore(host, port, collection, dimension, timeoutMs);
    }
}
