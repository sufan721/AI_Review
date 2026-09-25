package com.aireview.index;

/**
 * 索引侧对资料（note/document）的读写抽象。所有读取都带上 userId 做归属校验。
 */
public interface IndexResourceRepository {
    /** 按 id 与 userId 读取资料；不存在或非本人返回 null。 */
    IndexResource load(ResourceType type, long resourceId, long userId);

    void markIndexed(ResourceType type, long resourceId);

    void markFailed(ResourceType type, long resourceId, String reason);

    void markPending(ResourceType type, long resourceId);
}
