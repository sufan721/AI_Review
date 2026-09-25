package com.aireview.index;

import java.util.List;

/**
 * 文本嵌入抽象。返回的向量维度必须与 {@link VectorStore} 使用的集合维度一致。
 */
public interface EmbeddingClient {
    List<float[]> embed(List<String> texts);
}
