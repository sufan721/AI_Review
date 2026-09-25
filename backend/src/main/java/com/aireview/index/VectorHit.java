package com.aireview.index;

/**
 * 一次向量检索命中的切片。
 */
public record VectorHit(
    long chunkId,
    long resourceId,
    int contentVersion,
    String content,
    float score
) {
}
