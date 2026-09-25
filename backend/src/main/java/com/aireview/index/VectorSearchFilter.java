package com.aireview.index;

/**
 * 向量检索过滤条件。检索必须携带 {@code userId} 与资料范围，从根上保证租户隔离与版本一致。
 */
public record VectorSearchFilter(
    long userId,
    ResourceType resourceType,
    long resourceId,
    int contentVersion,
    int topK
) {
}
