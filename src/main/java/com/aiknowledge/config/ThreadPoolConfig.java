package com.aiknowledge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置
 * 支持AI接口高并发调用
 */
@Configuration
public class ThreadPoolConfig {

    @Value("${thread-pool.core-size:10}")
    private int coreSize;

    @Value("${thread-pool.max-size:50}")
    private int maxSize;

    @Value("${thread-pool.queue-capacity:200}")
    private int queueCapacity;

    @Value("${thread-pool.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("ai-task-");
        // 拒绝策略：由调用方线程执行（不丢弃任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
