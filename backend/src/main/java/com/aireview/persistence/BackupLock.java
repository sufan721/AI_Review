package com.aireview.persistence;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 备份单飞锁：JVM 内 synchronized 挡住单实例的并发请求；
 * Redis SETNX 作为多实例部署下的兜底，Redis 不可用时自动降级为仅本地锁。
 */
@Component
public class BackupLock {

    private static final Logger log = LoggerFactory.getLogger(BackupLock.class);
    private static final String KEY = "aireview:persistence:backup-lock";
    private static final Duration TTL = Duration.ofMinutes(30);

    @Autowired(required = false)
    private StringRedisTemplate redis;

    @Value("${ai-review.persistence.redis-lock-enabled:true}")
    private boolean redisLockEnabled;

    /** 获取分布式锁；返回 null 表示 Redis 不可用或已被其他实例持有（由调用方区分）。 */
    public String acquire() {
        if (!redisLockEnabled || redis == null) {
            return "local";
        }
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(KEY, token, TTL);
            if (Boolean.TRUE.equals(ok)) {
                return token;
            }
            return null;
        } catch (Exception exception) {
            log.warn("Redis 备份锁不可用，降级为单实例锁：{}", exception.getMessage());
            return "local";
        }
    }

    public void release(String token) {
        if (redis == null || token == null || "local".equals(token)) {
            return;
        }
        try {
            redis.execute((RedisCallback<Void>) connection -> {
                byte[] key = KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] value = token.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (java.util.Arrays.equals(connection.get(key), value)) {
                    connection.del(key);
                }
                return null;
            });
        } catch (Exception exception) {
            log.warn("释放 Redis 备份锁失败（TTL 到期后会自动释放）：{}", exception.getMessage());
        }
    }
}
