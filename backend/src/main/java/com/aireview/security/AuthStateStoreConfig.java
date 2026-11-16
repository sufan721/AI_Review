package com.aireview.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class AuthStateStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "ai-review.auth.store", havingValue = "redis", matchIfMissing = true)
    public AuthStateStore redisAuthStateStore(StringRedisTemplate redis) {
        return new RedisAuthStateStore(redis);
    }

    @Bean
    @ConditionalOnProperty(name = "ai-review.auth.store", havingValue = "memory")
    public AuthStateStore inMemoryAuthStateStore() {
        return new InMemoryAuthStateStore();
    }
}
