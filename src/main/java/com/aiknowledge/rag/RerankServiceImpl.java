package com.aiknowledge.rag;

import com.aiknowledge.model.entity.DocumentChunk;
import com.aiknowledge.service.AiService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cross-Encoder 重排序实现
 *
 * 核心思路：
 * 1. 将 (query, document) 成对输入LLM，让模型打分
 * 2. 使用结构化Prompt，要求模型输出 0-10 的相关度分数
 * 3. 根据分数重新排序，选出最相关的文档
 *
 * 支持三种策略：
 * - cross-encoder：使用LLM判断相关性（最准确，但增加延迟）
 * - hybrid-score：向量得分 + 关键词得分的线性组合（快速）
 * - rrf-only：不额外计算，直接使用RRF融合得分（最快）
 */
@Slf4j
@Component
public class RerankServiceImpl implements RerankService {

    private final AiService aiService;

    public RerankServiceImpl(AiService aiService) {
        this.aiService = aiService;
    }

    @Value("${rag.rerank.strategy:cross-encoder}")
    private String strategy;

    @Value("${rag.rerank.batch-size:8}")
    private int batchSize;

    // ========== 常量定义 ==========

    private static final String RERANK_PROMPT_TEMPLATE = """
            你是一个文档相关性评分专家。请评估以下【问题】和【文档】的相关程度。

            【问题】
            %s

            【文档】
            %s

            请只输出一个0到10之间的整数分数，表示该文档与问题的相关程度：
            - 9-10: 文档直接回答了问题，信息完全匹配
            - 7-8: 文档高度相关，包含大部分所需信息
            - 5-6: 文档部分相关，有一些有用信息
            - 3-4: 文档弱相关，仅有少量相关信息
            - 0-2: 文档与问题基本无关

            分数：
            """;

    // ========== 接口实现 ==========

    @Override
    public List<DocumentChunk> rerank(String query, List<DocumentChunk> docs, int topK) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        if (topK >= docs.size()) {
            topK = docs.size();
        }

        log.info("=== 开始重排序 | 策略={} | 输入={}条 | 返回top-{} ===", strategy, docs.size(), topK);

        long startTime = System.currentTimeMillis();

        List<DocumentChunk> result = switch (strategy.toLowerCase()) {
            case "cross-encoder" -> crossEncoderRerank(query, docs, topK);
            case "hybrid-score" -> hybridScoreRerank(docs, topK);
            default -> fallbackRerank(docs, topK);
        };

        long costMs = System.currentTimeMillis() - startTime;

        log.info("=== 重排序完成 | 耗时{}ms | 输出{}条 ===", costMs, result.size());

        printRerankResults(result, query);

        return result;
    }

    // ========== 策略实现 ==========

    /**
     * 策略1：Cross-Encoder 重排序
     * 使用 LLM 对每个 (query, doc) 对进行相关性评分
     */
    private List<DocumentChunk> crossEncoderRerank(String query, List<DocumentChunk> docs, int topK) {
        List<ScoredDocument> scoredDocs = new ArrayList<>();

        for (int i = 0; i < docs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, docs.size());
            List<DocumentChunk> batch = docs.subList(i, end);

            for (DocumentChunk doc : batch) {
                double score = scoreWithLlm(query, doc);
                scoredDocs.add(new ScoredDocument(doc, score));

                log.debug("Cross-Encoder评分: source={}, score={}", doc.getSource(), String.format("%.2f", score));
            }
        }

        return selectTopK(scoredDocs, topK);
    }

    /**
     * 策略2：混合得分重排序
     * 结合 vector_score + keyword_score 加权
     */
    private List<DocumentChunk> hybridScoreRerank(List<DocumentChunk> docs, int topK) {
        List<ScoredDocument> scoredDocs = new ArrayList<>();

        for (DocumentChunk doc : docs) {
            double finalScore = doc.getScore();

            Double keywordScore = doc.getKeywordScore();
            if (keywordScore != null && keywordScore > 0) {
                finalScore = 0.7 * doc.getScore() + 0.3 * keywordScore;
            }

            scoredDocs.add(new ScoredDocument(doc, finalScore));
        }

        return selectTopK(scoredDocs, topK);
    }

    /**
     * 兜底方案
     */
    private List<DocumentChunk> fallbackRerank(List<DocumentChunk> docs, int topK) {
        log.info("使用兜底重排序策略，直接返回前{}条", topK);
        return new ArrayList<>(docs.subList(0, Math.min(topK, docs.size())));
    }

    // ========== 内部工具方法 ==========

    private double scoreWithLlm(String query, DocumentChunk doc) {
        try {
            String prompt = buildRerankPrompt(query, doc);
            String response = aiService.ask(prompt, "请给出分数。");
            return parseScore(response);
        } catch (Exception e) {
            log.warn("LLM评分失败，使用原始相似度: error={}", e.getMessage());
            return doc.getScore();
        }
    }

    private String buildRerankPrompt(String query, DocumentChunk doc) {
        String content = doc.getContent();
        if (content.length() > 500) {
            content = content.substring(0, 500) + "...";
        }
        return String.format(RERANK_PROMPT_TEMPLATE, query, content);
    }

    private double parseScore(String response) {
        if (response == null || response.isBlank()) {
            return 5.0 / 10.0;
        }
        try {
            String cleaned = response.replaceAll("[^0-9.]", "").trim();
            if (!cleaned.isEmpty()) {
                double score = Double.parseDouble(cleaned);
                return Math.max(0, Math.min(10, score)) / 10.0;
            }
        } catch (NumberFormatException ignored) {}
        return 5.0 / 10.0;
    }

    private List<DocumentChunk> selectTopK(List<ScoredDocument> scoredDocs, int topK) {
        return scoredDocs.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .peek(sd -> sd.document.setRerankScore(sd.score))
                .map(sd -> sd.document)
                .collect(Collectors.toList());
    }

    private void printRerankResults(List<DocumentChunk> results, String query) {
        System.out.println("\n========== 重排序结果（Final） ==========");
        System.out.println("查询问题: " + query);
        System.out.println("返回数量: " + results.size());

        for (int i = 0; i < results.size(); i++) {
            DocumentChunk chunk = results.get(i);
            System.out.printf("%n--- 第%d名 ---%n", i + 1);
            System.out.printf("综合得分: %.4f%n", chunk.getScore());
            if (chunk.getRerankScore() != null) {
                System.out.printf("重排得分: %.4f%n", chunk.getRerankScore());
            }
            System.out.println("来源: " + chunk.getSource());
            String preview = chunk.getContent().length() > 150
                    ? chunk.getContent().substring(0, 150) + "..."
                    : chunk.getContent();
            System.out.println("内容: " + preview);
        }
        System.out.println("======================================\n");
    }

    // ========== 内部数据结构 ==========

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    private static class ScoredDocument {
        private DocumentChunk document;
        private double score;
    }
}
