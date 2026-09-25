package com.aireview.index;

/**
 * 索引流水线编排：切片、嵌入、写入向量库、状态流转与删除同步。
 */
public interface IndexingService {
    /** 异步投递索引任务；从 {@code UserContext} 捕获当前用户。 */
    void schedule(ResourceType type, long resourceId);

    /** 同步执行一次完整索引（供执行器与单测调用）；已索引则幂等跳过。 */
    void indexNow(ResourceType type, long resourceId, long userId);

    /** 删除资料对应的切片与向量（尽力而为，不抛异常）。 */
    void deleteResource(ResourceType type, long resourceId);

    /** 手动重试：置回 PENDING 并异步投递。 */
    void reindex(ResourceType type, long resourceId);
}
