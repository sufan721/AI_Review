package com.aireview.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 快照/恢复的专用有界线程池：备份期间不占用 Web 线程，也不会无限排队打爆内存。 */
@Configuration
@EnableScheduling
public class PersistenceExecutorConfig {

    @Bean("backupTaskExecutor")
    public ThreadPoolTaskExecutor backupTaskExecutor(
        @Value("${ai-review.persistence.worker-core:1}") int core,
        @Value("${ai-review.persistence.worker-max:2}") int max,
        @Value("${ai-review.persistence.worker-queue:4}") int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix("aireview-backup-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
