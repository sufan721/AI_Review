package com.aireview.index;

/**
 * 索引侧看到的统一「资料」视图，屏蔽笔记与文件两张表的差异。
 */
public record IndexResource(
    long resourceId,
    long userId,
    String content,
    int contentVersion,
    String indexStatus
) {
}
