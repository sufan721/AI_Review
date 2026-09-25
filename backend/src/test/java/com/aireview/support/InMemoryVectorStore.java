package com.aireview.support;

import com.aireview.index.ResourceType;
import com.aireview.index.VectorHit;
import com.aireview.index.VectorRecord;
import com.aireview.index.VectorSearchFilter;
import com.aireview.index.VectorStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 内存向量库测试双：按 {@code resourceType + resourceId} 整体替换，检索严格按
 * {@code userId / resourceType / resourceId / contentVersion} 过滤，与 {@code MilvusVectorStore}
 * 的过滤语义一致。
 */
public class InMemoryVectorStore implements VectorStore {
    private final List<VectorRecord> records = new ArrayList<>();

    @Override
    public synchronized void replace(ResourceType resourceType, long resourceId, List<VectorRecord> records) {
        this.records.removeIf(r -> r.resourceType() == resourceType && r.resourceId() == resourceId);
        this.records.addAll(records);
    }

    @Override
    public synchronized void delete(ResourceType resourceType, long resourceId) {
        records.removeIf(r -> r.resourceType() == resourceType && r.resourceId() == resourceId);
    }

    @Override
    public synchronized List<VectorHit> search(VectorSearchFilter filter, float[] queryVector) {
        return records.stream()
            .filter(r -> r.userId() == filter.userId())
            .filter(r -> r.resourceType() == filter.resourceType())
            .filter(r -> r.resourceId() == filter.resourceId())
            .filter(r -> r.contentVersion() == filter.contentVersion())
            .map(r -> new VectorHit(r.chunkId(), r.resourceId(), r.contentVersion(), r.content(),
                cosine(queryVector, r.vector())))
            .sorted(Comparator.comparingDouble(VectorHit::score).reversed())
            .limit(filter.topK())
            .toList();
    }

    /** 某份资料当前保存的全部向量记录。 */
    public synchronized List<VectorRecord> recordsFor(ResourceType resourceType, long resourceId) {
        return records.stream()
            .filter(r -> r.resourceType() == resourceType && r.resourceId() == resourceId)
            .toList();
    }

    public synchronized void clear() {
        records.clear();
    }

    private static float cosine(float[] a, float[] b) {
        float dot = 0f;
        float normA = 0f;
        float normB = 0f;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0f || normB == 0f) {
            return 0f;
        }
        return dot / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
