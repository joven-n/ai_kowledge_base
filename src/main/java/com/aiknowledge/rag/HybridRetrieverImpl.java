package com.aiknowledge.rag;

import com.aiknowledge.model.entity.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索器实现
 *
 * 核心流程：
 * 1. 向量检索 → top-k 条语义相关文档
 * 2. 关键词检索 → top-k 条关键词匹配文档
 * 3. RRF融合 → 合并去重，统一排序
 *
 * RRF公式：score(d) = Σ 1/(k + rank_i(d))
 * 其中 k=60（标准值），rank_i 是文档在 第i个结果列表中的排名
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetrieverImpl implements HybridRetriever {

    private final VectorStore vectorStore;
    private final KeywordIndex keywordIndex;

    @Value("${rag.hybrid-search.vector-top-k:8}")
    private int vectorTopK;

    @Value("${rag.hybrid-search.keyword-top-k:8}")
    private int keywordTopK;

    @Value("${rag.hybrid-search.rrf-k:60}")
    private int rrfK;

    @Override
    public List<DocumentChunk> hybridSearch(String query, float[] queryVector, int finalTopK) {
        long startTime = System.currentTimeMillis();
        log.info("=== 开始混合检索 | query={} | finalTopK={}", 
            query.length() > 50 ? query.substring(0, 50) + "..." : query, finalTopK);

        // ===== 第一路：向量检索 =====
        List<DocumentChunk> vectorResults = vectorStore.search(queryVector, vectorTopK);
        log.debug("向量检索完成: {}条", vectorResults.size());

        printSearchResults("向量检索", vectorResults);

        // ===== 第二路：关键词检索 =====
        List<DocumentChunk> keywordResults = keywordIndex.search(query, keywordTopK);
        log.debug("关键词检索完成: {}条", keywordResults.size());

        printSearchResults("关键词检索", keywordResults);

        // ===== RRF融合 =====
        List<DocumentChunk> fused = rrfFusion(vectorResults, keywordResults);

        // ===== 截取最终TopK =====
        List<DocumentChunk> result = fused.stream()
                .limit(finalTopK)
                .collect(Collectors.toList());

        long costMs = System.currentTimeMillis() - startTime;
        log.info("=== 混合检索完成 | 向量{}条 + 关键词{}条 → 融合{}条 → 最终{}条 | 耫时{}ms ===",
                vectorResults.size(), keywordResults.size(), fused.size(), result.size(), costMs);

        printHybridResults(result, query);

        return result;
    }

    /**
     * Reciprocal Rank Fusion 算法
     * 
     * 公式: score(d) = Σ 1/(k + rank_i(d))
     * 
     * 优点：
     * - 不需要归一化不同检索器的得分
     * - 对异常分数鲁棒
     * - 实现简单效果好
     */
    private List<DocumentChunk> rrfFusion(
            List<DocumentChunk> vectorResults, 
            List<DocumentChunk> keywordResults) {
        
        Map<String, RrfScore> scoreMap = new LinkedHashMap<>();

        // 处理向量检索结果
        addScores(scoreMap, vectorResults, 1.0); // weight=1.0

        // 处理关键词检索结果
        addScores(scoreMap, keywordResults, 1.0); // weight=1.0

        // 按RRF得分降序排列
        return scoreMap.values().stream()
                .sorted((a, b) -> Double.compare(b.rrfScore, a.rrfScore))
                .peek(doc -> {
                    // 更新文档的融合得分
                    doc.getDocument().setScore(doc.getRrfScore());
                })
                .map(RrfScore::getDocument)
                .collect(Collectors.toList());
    }

    /**
     * 将一个检索结果列表添加到RRF得分映射中
     */
    private void addScores(Map<String, RrfScore> scoreMap, 
                           List<DocumentChunk> results, double weight) {
        int rank = 1;
        for (DocumentChunk doc : results) {
            String key = generateDocKey(doc);
            
            double rrfContribution = weight / (rrfK + rank);

            RrfScore existing = scoreMap.get(key);
            if (existing != null) {
                // 已存在，累加RRF得分
                existing.setRrfScore(existing.getRrfScore() + rrfContribution);
            } else {
                // 新文档
                scoreMap.put(key, new RrfScore(doc, rrfContribution));
            }
            
            rank++;
        }
    }

    /**
     * 生成文档唯一标识（用于去重）
     * 使用 chunkId 或 content hash 作为key
     */
    private String generateDocKey(DocumentChunk doc) {
        if (doc.getChunkId() != null && !doc.getChunkId().isBlank()) {
            return doc.getChunkId();
        }
        // 降级：使用来源+内容长度作为简易标识
        return doc.getSource() + "_" + doc.getContent().hashCode();
    }

    // ========== 打印方法 ==========

    private void printSearchResults(String label, List<DocumentChunk> results) {
        System.out.println("\n--- " + label + " ---");
        for (int i = 0; i < results.size(); i++) {
            DocumentChunk d = results.get(i);
            String src = d.getSource() != null ? d.getSource() : "unknown";
            String preview = d.getContent().length() > 80 
                    ? d.getContent().substring(0, 80) + "..." : d.getContent();
            System.out.printf("  #%d [score=%.4f] [%s] %s%n", 
                i+1, d.getScore(), src, preview);
        }
    }

    private void printHybridResults(List<DocumentChunk> results, String query) {
        System.out.println("\n========== 混合检索结果（RRF融合后） ==========");
        System.out.println("查询: " + (query.length() > 60 ? query.substring(0, 60) + "..." : query));
        System.out.println("总数: " + results.size());
        
        for (int i = 0; i < results.size(); i++) {
            DocumentChunk d = results.get(i);
            System.out.printf("%n--- #%d [rrf=%.4f] ---%n", i+1, d.getScore());
            System.out.println("来源: " + d.getSource());
            String preview = d.getContent().length() > 120 
                    ? d.getContent().substring(0, 120) + "..." : d.getContent();
            System.out.println("内容: " + preview);
        }
        System.out.println("=============================================\n");
    }

    // ========== 内部数据结构 ==========

    /**
     * RRF得分包装类
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class RrfScore {
        private DocumentChunk document;
        private double rrfScore;
    }
}
