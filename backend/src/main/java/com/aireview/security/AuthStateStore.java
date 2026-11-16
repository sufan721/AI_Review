package com.aireview.security;

import java.time.Duration;

/**
 * 带 TTL 的键值状态存储：验证码、发送限频、刷新令牌、登出黑名单、SSO state 等。
 *
 * <p>生产走 Redis，测试或单机轻量部署可用内存实现，二者对外语义一致。
 */
public interface AuthStateStore {

    void put(String key, String value, Duration ttl);

    /** 一次性消费：读取后立即删除；不存在返回 null。 */
    String getAndDelete(String key);

    /** 仅当 key 不存在时写入（用于限频），返回是否写入成功。 */
    boolean setIfAbsent(String key, String value, Duration ttl);

    boolean exists(String key);

    void delete(String key);
}
