package com.aireview.support;

import com.aireview.index.EmbeddingClient;
import com.aireview.index.IndexTaskSink;
import com.aireview.index.VectorStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 集成测试的索引测试双装配。以 {@code @Primary} 覆盖 {@code IndexConfig} 中会访问外部的真实实现
 * （线程池、百炼嵌入、Milvus），使测试无需 Milvus/百炼、且执行结果确定可控。
 */
@TestConfiguration
public class IndexingTestConfig {
    @Bean
    public ControlledTaskExecutor controlledTaskExecutor() {
        return new ControlledTaskExecutor();
    }

    @Bean
    @Primary
    public IndexTaskSink testIndexTaskSink(ControlledTaskExecutor executor) {
        return executor;
    }

    @Bean
    public FakeEmbeddingClient fakeEmbeddingClient() {
        return new FakeEmbeddingClient(8);
    }

    @Bean
    @Primary
    public EmbeddingClient testEmbeddingClient(FakeEmbeddingClient client) {
        return client;
    }

    @Bean
    public InMemoryVectorStore inMemoryVectorStore() {
        return new InMemoryVectorStore();
    }

    @Bean
    @Primary
    public VectorStore testVectorStore(InMemoryVectorStore store) {
        return store;
    }
}
