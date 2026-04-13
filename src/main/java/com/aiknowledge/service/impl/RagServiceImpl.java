package com.aiknowledge.service.impl;

import com.aiknowledge.config.AiProperties;
import com.aiknowledge.config.AiProperties;
import com.aiknowledge.model.dto.AskRequest;
import com.aiknowledge.model.entity.DocumentChunk;
import com.aiknowledge.model.vo.AskResponse;
import com.aiknowledge.rag.HybridRetriever;
import com.aiknowledge.rag.RerankService;
import com.aiknowledge.service.AiService;
import com.aiknowledge.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG服务实现（增强版）
 *
 * 核心流程（三阶段流水线）：
 *
 * ┌─────────────┐    ┌─────────────────┐    ┌──────────┐    ┌──────────┐
 * │ 用户问题     │ -> │ 混合检索(Hybrid) │ -> │ 重排序   │ -> │ LLM生成  │
 * │             │    │ 向量 + 关键词     │    │ Rerank   │    │          │
 * └─────────────┘    │ RRF融合          │    │ Top-K精选│    └──────────┘
 *                     └─────────────────┘    └──────────┘
 *
 * 阶段1 - 混合检索（Hybrid Search）：
 *   - 向量检索：语义相似度匹配（top-8）
 *   - 关键词检索：BM25精确匹配（top-8）
 *   - RRF融合：Reciprocal Rank Fusion 合并两路结果
 *
 * 阶段2 - 重排序（Rerank）：
 *   - Cross-Encoder：LLM判断问题-文档相关性
 *   - 最终返回 top-3 条最相关文档
 *
 * 阶段3 - LLM生成：
 *   - 基于精选文档构造增强Prompt
 *   - 调用大模型生成答案
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final AiService aiService;
    private final HybridRetriever hybridRetriever;
    private final RerankService rerankService;
    private final AiProperties aiProperties;

    // ========== 配置项 ==========

    @Value("${rag.hybrid-search.enabled:true}")
    private boolean hybridSearchEnabled;

    @Value("${rag.rerank.enabled:true}")
    private boolean rerankEnabled;

    @Value("${rag.similarity-threshold:0.3}")
    private double similarityThreshold;

    // 向量检索直接使用的top-k（混合检索关闭时的降级方案）
    @Value("${rag.top-k:8}")
    private int fallbackTopK;

    // 混合检索最终候选数量（RRF融合后）
    @Value("${rag.hybrid-search.candidate-top-k:8}")
    private int hybridCandidateTopK;

    // Rerank最终返回数量
    @Value("${rag.rerank.final-top-k:3}")
    private int rerankFinalTopK;

    /**
     * RAG Prompt模板 - 专业设计，减少幻觉，提升准确率
     */
    private static final String RAG_SYSTEM_PROMPT = """
            你是一个专业的AI助手，请严格基于以下【参考资料】回答用户问题。

            【参考资料】
            %s

            【回答规则】
            1. 只使用参考资料中的信息回答问题
            2. 如果参考资料中没有相关信息，回答"根据知识库中的资料，暂未找到相关信息"
            3. 不要编造、推测或添加参考资料之外的内容
            4. 回答要准确、简洁、专业
            5. 如果问题涉及多个资料片段，请综合归纳后回答
            """;

    @Override
    public AskResponse ragAsk(AskRequest request) {
        long start = System.currentTimeMillis();
        String question = request.getQuestion();

        log.info("╔══════════════════════════════════════╗");
        log.info("║  RAG问答开始                          ║");
        log.info("║  问题: {}                        ║", 
                question.length() > 50 ? question.substring(0, 50) + "..." : question);
        log.info("╚══════════════════════════════════════╝");

        // ===== Step 1: 问题向量化 =====
        float[] questionVector = aiService.embedding(question);
        log.debug("问题向量化完成，维度: {}", questionVector.length);

        // ===== Step 2: 文档检索（混合 / 纯向量） =====
        List<DocumentChunk> candidates;

        if (hybridSearchEnabled) {
            // 【增强模式】混合检索 → RRF融合 → 返回候选集
            log.info("[阶段1] 启动混合检索...");
            candidates = hybridRetriever.hybridSearch(question, questionVector, hybridCandidateTopK);
            log.info("[阶段1] 混合检索完成: 候选文档={}条", candidates.size());
        } else {
            // 【降级模式】纯向量检索
            log.info("[阶段1] 使用纯向量检索（混合检索已禁用）");
            List<DocumentChunk> vectorResults = hybridRetriever.hybridSearch(
                question, questionVector, fallbackTopK);
            candidates = vectorResults;
            log.info("[阶段1] 向量检索完成: 候选文档={}条", candidates.size());
        }

        // 打印候选结果详情
        printCandidates(candidates, question);

        // ===== Step 3: 相似度过滤（兜底保护） =====
        List<DocumentChunk> filtered = candidates.stream()
                .filter(chunk -> chunk.getScore() >= similarityThreshold)
                .collect(Collectors.toList());

        log.info("[阈值过滤] {}条 → {}条（阈值={})", candidates.size(), filtered.size(), similarityThreshold);

        if (filtered.isEmpty()) {
            log.warn("[阈值过滤] 全部过滤！使用原始候选集中得分最高的{}条", Math.min(2, candidates.size()));
            filtered = candidates.isEmpty() ? new ArrayList<>() : candidates.subList(0, Math.min(2, candidates.size()));
        }

        // ===== Step 4: 重排序（Rerank） =====
        List<DocumentChunk> finalDocs;

        if (rerankEnabled && filtered.size() > rerankFinalTopK) {
            log.info("[阶段2] 启动重排序... (策略: cross-encoder)");
            finalDocs = rerankService.rerank(question, filtered, rerankFinalTopK);
            log.info("[阶段2] 重排序完成: 最终文档={}条", finalDocs.size());
        } else {
            log.info("[阶段2] 跳过重排序（已禁用或文档数量≤{}）", rerankFinalTopK);
            finalDocs = filtered.size() > rerankFinalTopK 
                    ? new ArrayList<>(filtered.subList(0, rerankFinalTopK))
                    : new ArrayList<>(filtered);
        }

        // ===== Step 5: 构造上下文 =====
        String context = buildContext(finalDocs);
        List<String> sources = extractSources(finalDocs);

        // ===== Step 6: 构造Prompt & 调用LLM =====
        String systemPrompt = finalDocs.isEmpty()
                ? "你是一个专业的AI助手，请尽力回答用户问题。如果不确定请如实说明。"
                : String.format(RAG_SYSTEM_PROMPT, context);

        log.info("[阶段3] 调用LLM生成答案...");
        String answer = aiService.ask(systemPrompt, question);

        long cost = System.currentTimeMillis() - start;
        
        // ===== 结果输出 =====
        log.info("╔══════════════════════════════════════╗");
        log.info("║  RAG问答完成                          ║");
        log.info("║  耗时: {}ms                       ║", cost);
        log.info("║  来源: {}                      ║", sources);
        log.info("╚══════════════════════════════════════╝");

        return AskResponse.builder()
                .answer(answer)
                .sources(sources)
                .model(aiProperties.getChatModel())
                .costMs(cost)
                .build();
    }

    // ==================== 工具方法 ====================

    /**
     * 构造上下文字符串
     * 将多个切片内容拼接并编号，方便LLM引用
     */
    private String buildContext(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) return "（暂无相关资料）";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            
            sb.append(String.format("[资料%d]（来源：%s", i + 1, chunk.getSource()));
            
            // 显示多种得分信息
            sb.append(String.format("，融合得分：%.3f", chunk.getScore()));
            if (chunk.getKeywordScore() != null) {
                sb.append(String.format("，关键词：%.3f", chunk.getKeywordScore()));
            }
            if (chunk.getRerankScore() != null) {
                sb.append(String.format("，重排：%.3f", chunk.getRerankScore()));
            }
            sb.append("）\n");
            
            sb.append(chunk.getContent());
            sb.append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 提取引用来源（去重）
     */
    private List<String> extractSources(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(DocumentChunk::getSource)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 打印候选文档详情（用于调试和效果观察）
     */
    private void printCandidates(List<DocumentChunk> candidates, String query) {
        System.out.println("\n═══════════════════════════════════════");
        System.out.println("  📋 候选文档详情（重排序前）");
        System.out.println("  查询: " + query);
        System.out.println("  数量: " + candidates.size());
        System.out.println("═══════════════════════════════════════");

        for (int i = 0; i < candidates.size(); i++) {
            DocumentChunk c = candidates.get(i);
            System.out.printf("%n  [%d] 得分=%.4f | %s%n", i+1, c.getScore(), c.getSource());
            
            if (c.getKeywordScore() != null) {
                System.out.printf("      关键词BM25=%.4f%n", c.getKeywordScore());
            }
            
            String preview = c.getContent().length() > 100 
                    ? c.getContent().substring(0, 100) + "..." : c.getContent();
            System.out.println("      内容: " + preview);
        }
        System.out.println("\n═══════════════════════════════════════\n");
    }
}
