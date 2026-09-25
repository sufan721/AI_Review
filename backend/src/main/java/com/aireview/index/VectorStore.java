package com.aireview.index;

import java.util.List;

/**
 * 向量库抽象。生产实现为 {@link MilvusVectorStore}；测试使用内存实现。
 *
 * <p>所有操作以 {@code resourceType + resourceId} 为最小单位：替换时先删后写，
 * 从而保证任意时刻只有当前内容版本的切片可被检索。
 */
public interface VectorStore {
    /** 用给定记录整体替换某份资料的全部向量（先删除再写入）。 */
    void replace(ResourceType resourceType, long resourceId, List<VectorRecord> records);

    /** 删除某份资料的全部向量。 */
    void delete(ResourceType resourceType, long resourceId);

    /** 按过滤条件检索 topK 条切片，过滤器必含 user_id 与版本。 */
    List<VectorHit> search(VectorSearchFilter filter, float[] queryVector);
}
