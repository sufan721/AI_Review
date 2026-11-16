package com.aireview.security;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisAuthStateStore implements AuthStateStore {
    private final StringRedisTemplate redis;

    public RedisAuthStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    @Override
    public String getAndDelete(String key) {
        return redis.opsForValue().getAndDelete(key);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, value, ttl));
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }
}
