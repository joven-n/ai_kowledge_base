package com.aiknowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存配置（使用内存缓存，无需依赖Redis启动）
 *
 * 生产升级方案：
 * 1. 引入spring-boot-starter-data-redis
 * 2. 替换为 RedisCacheManager
 * 3. 修改 @Cacheable 等注解的 cacheManager 参数
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        log.info("初始化内存CacheManager（开发模式）");
        // 生产环境替换为 RedisCacheManager
        return new ConcurrentMapCacheManager(
                "ai_responses",    // AI响应缓存
                "embeddings",      // Embedding缓存（避免重复调用）
                "knowledge_docs"   // 知识库文档缓存
        );
    }
}
