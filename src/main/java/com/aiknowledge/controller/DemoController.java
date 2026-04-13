package com.aiknowledge.controller;

import com.aiknowledge.common.Result;
import com.aiknowledge.model.entity.DocumentChunk;
import com.aiknowledge.rag.InMemoryVectorStore;
import com.aiknowledge.rag.TextSplitter;
import com.aiknowledge.rag.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Demo接口（无需真实API Key，可直接测试框架功能）
 * 适合演示和本地开发
 */
@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final TextSplitter textSplitter;
    private final VectorStore vectorStore;

    /**
     * 测试文本切分
     * POST /demo/split
     *
      curl -X POST http://localhost:8080/demo/split -H "Content-Type: application/json" -d "{\"text\":\"这是一段很长的技术文档内容，用于测试文本切分功能。RAG技术通过检索增强生成，显著提升了大语言模型的准确性。\"}"
     */
    @PostMapping("/split")
    public Result<Map<String, Object>> testSplit(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return Result.error("text不能为空");
        }

        List<String> chunks = textSplitter.split(text);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalLength", text.length());
        result.put("chunkCount", chunks.size());
        result.put("chunks", chunks);

        return Result.success(result);
    }

    /**
     * 测试向量存储（注入随机向量模拟）
     * GET /demo/vector/stats
     */
    @GetMapping("/vector/stats")
    public Result<Map<String, Object>> vectorStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalChunks", vectorStore.count());
        stats.put("storeType", vectorStore.getClass().getSimpleName());
        return Result.success(stats);
    }

    /**
     * 注入测试数据（用随机向量）
     * POST /demo/vector/inject
     */
    @PostMapping("/vector/inject")
    public Result<String> injectTestData() {
        List<DocumentChunk> testChunks = new ArrayList<>();
        String[] testContents = {
                "RAG（Retrieval-Augmented Generation）是检索增强生成技术，结合了检索和生成能力。",
                "Spring Boot是Java生态中最流行的微服务框架，支持快速构建企业级应用。",
                "向量数据库用于存储和检索高维向量，支持相似度搜索，是RAG系统的核心组件。",
                "大语言模型（LLM）通过海量数据预训练，具备强大的文本理解和生成能力。",
                "Milvus是开源的向量数据库，支持十亿级向量的高效检索。"
        };

        Random random = new Random(42);
        for (int i = 0; i < testContents.length; i++) {
            // 生成随机向量（模拟embedding，仅用于演示）
            float[] embedding = new float[1536];
            for (int j = 0; j < 1536; j++) {
                embedding[j] = (float) (random.nextGaussian() * 0.1);
            }
            // 让第一个chunk和查询向量更相似（演示用）
            if (i == 0) Arrays.fill(embedding, 0.1f);

            testChunks.add(DocumentChunk.builder()
                    .chunkId("test_" + i)
                    .documentId("test_doc")
                    .source("测试数据")
                    .content(testContents[i])
                    .embedding(embedding)
                    .chunkIndex(i)
                    .build());
        }

        vectorStore.addChunks(testChunks);
        return Result.success("成功注入" + testChunks.size() + "条测试向量数据");
    }

    /**
     * 系统架构说明
     * GET /demo/architecture
     */
    @GetMapping("/architecture")
    public Result<Map<String, Object>> architecture() {
        Map<String, Object> arch = new LinkedHashMap<>();
        arch.put("projectName", "AI知识库问答系统 (RAG + Agent)");
        arch.put("version", "1.0.0");
        arch.put("技术栈", Map.of(
                "后端框架", "Spring Boot 3.2 + Java 17",
                "AI调用", "OpenAI API / 兼容接口",
                "向量数据库", "Milvus (生产) / 内存向量库 (开发)",
                "缓存", "Redis / 内存缓存",
                "限流", "Resilience4j RateLimiter",
                "HTTP客户端", "OkHttp3",
                "文档解析", "Apache PDFBox"
        ));
        arch.put("核心模块", List.of(
                "AiService - AI调用门面（支持多LLM接入）",
                "RagService - RAG检索增强生成",
                "KnowledgeService - 知识库管理",
                "ConversationService - 多轮对话管理",
                "AgentExecutor - ReAct Agent引擎",
                "VectorStore - 向量存储接口（可切换实现）",
                "TextSplitter - 智能文本切分"
        ));
        arch.put("API接口", List.of(
                "POST /ai/ask - 基础问答",
                "POST /ai/rag - RAG知识库问答",
                "POST /ai/chat - 多轮对话",
                "POST /ai/agent - Agent工具调用",
                "POST /kb/upload - 文档上传",
                "GET  /system/health - 健康检查"
        ));
        return Result.success(arch);
    }
}
