package com.aireview.support;

import com.aireview.index.EmbeddingClient;
import com.aireview.index.IndexingException;
import java.util.List;

/**
 * 确定性的嵌入客户端：按文本散列生成固定维度向量，避免测试依赖外部模型。
 * 可设置 {@link #setFail(boolean)} 模拟嵌入失败，用于覆盖失败与重试路径。
 */
public class FakeEmbeddingClient implements EmbeddingClient {
    private final int dimension;
    private volatile boolean fail;

    public FakeEmbeddingClient(int dimension) {
        this.dimension = dimension;
    }

    public void setFail(boolean fail) {
        this.fail = fail;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (fail) {
            throw new IndexingException("模拟嵌入失败");
        }
        return texts.stream().map(this::vectorFor).toList();
    }

    private float[] vectorFor(String text) {
        float[] vector = new float[dimension];
        int hash = text.hashCode();
        for (int i = 0; i < dimension; i++) {
            vector[i] = (float) Math.sin(i * 0.7 + hash * 0.001);
        }
        return vector;
    }
}
