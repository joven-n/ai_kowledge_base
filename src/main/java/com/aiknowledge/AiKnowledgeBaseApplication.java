package com.aiknowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI知识库问答系统主入口
 * 支持 RAG + Agent + 多轮对话
 *
 * @author AI Knowledge Base Team
 */
@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisReactiveAutoConfiguration.class
})
@EnableCaching
@EnableAsync
public class AiKnowledgeBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiKnowledgeBaseApplication.class, args);
        System.out.println("""
                ╔══════════════════════════════════════════╗
                ║   AI Knowledge Base RAG System Started   ║
                ║   http://localhost:8080                  ║
                ║   /ai/ask   - 基础问答                   ║
                ║   /ai/rag   - RAG知识库问答              ║
                ║   /ai/chat  - 多轮对话                   ║
                ║   /ai/agent - Agent工具调用              ║
                ║   /kb/upload - 上传知识文档              ║
                ╚══════════════════════════════════════════╝
                """);
    }
}
