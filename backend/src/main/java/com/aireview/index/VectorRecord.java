package com.aireview.index;

/**
 * 待写入向量库的一条切片记录。{@code userId} 用于强制租户隔离，
 * {@code contentVersion} 用于排除旧版本。
 */
public record VectorRecord(
    long userId,
    ResourceType resourceType,
    long resourceId,
    long chunkId,
    int chunkIndex,
    int contentVersion,
    String content,
    float[] vector
) {
}
