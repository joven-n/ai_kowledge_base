package com.aiknowledge.rag;

import com.aiknowledge.model.entity.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存版关键词倒排索引（BM25算法）
 *
 * 功能：
 * 1. 构建倒排索引：token → document list
 * 2. BM25 相关性评分
 * 3. 动态增删文档
 */
@Slf4j
@Component
public class KeywordIndex {

    private final Map<String, List<TokenPosting>> index = new ConcurrentHashMap<>();
    private final Map<String, Integer> docLengths = new ConcurrentHashMap<>();
    private double avgDocLength = 0.0;
    private int totalDocs = 0;

    // BM25参数
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    // ========== 公共接口 ==========

    public synchronized void addDocument(DocumentChunk doc) {
        String docKey = getDocKey(doc);

        if (docLengths.containsKey(docKey)) {
            removeDocument(doc);
        }

        List<String> tokens = tokenize(doc.getContent());
        docLengths.put(docKey, tokens.size());
        totalDocs++;

        // 统计词频
        Map<String, Integer> termFreq = new HashMap<>();
        for (String token : tokens) {
            termFreq.merge(token, 1, Integer::sum);
        }

        // 更新倒排索引
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String token = entry.getKey();
            int tf = entry.getValue();
            index.computeIfAbsent(token, k -> new ArrayList<>())
                 .add(new TokenPosting(doc, tf));
        }

        recalcAvgDocLength();
        log.debug("添加文档到关键词索引: key={}, tokens={}", docKey, tokens.size());
    }

    public void addDocuments(List<DocumentChunk> docs) {
        for (DocumentChunk doc : docs) {
            addDocument(doc);
        }
    }

    public synchronized void removeDocument(DocumentChunk doc) {
        String docKey = getDocKey(doc);

        if (docLengths.remove(docKey) != null) {
            totalDocs--;
            
            for (List<TokenPosting> postings : index.values()) {
                postings.removeIf(p -> getDocKey(p.doc).equals(docKey));
            }
            
            index.entrySet().removeIf(e -> e.getValue().isEmpty());
            recalcAvgDocLength();
        }
    }

    public synchronized void clear() {
        index.clear();
        docLengths.clear();
        totalDocs = 0;
        avgDocLength = 0.0;
    }

    /**
     * 关键词搜索（BM25评分）
     */
    public List<DocumentChunk> search(String query, int topK) {
        if (query == null || query.isBlank() || totalDocs == 0) {
            return List.of();
        }

        long start = System.currentTimeMillis();
        List<String> queryTokens = tokenize(query);

        Map<DocumentChunk, Double> scores = new LinkedHashMap<>();

        for (String token : queryTokens) {
            List<TokenPosting> postings = index.get(token);
            if (postings == null || postings.isEmpty()) continue;

            int df = postings.size();
            double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);

            for (TokenPosting posting : postings) {
                DocumentChunk doc = posting.doc;
                int tf = posting.termFrequency;
                String docKey = getDocKey(doc);
                int dl = docLengths.getOrDefault(docKey, 1);

                double tfComponent = (tf * (K1 + 1.0)) / (tf + K1 * (1.0 - B + B * dl / avgDocLength));
                double score = idf * tfComponent;
                scores.merge(doc, score, Double::sum);
            }
        }

        List<DocumentChunk> results = scores.entrySet().stream()
                .sorted(Map.Entry.<DocumentChunk, Double>comparingByValue().reversed())
                .limit(topK)
                .peek(entry -> entry.getKey().setKeywordScore(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        long cost = System.currentTimeMillis() - start;
        log.debug("关键词搜索: tokens={}, 结果={}, 耗时{}ms", queryTokens.size(), results.size(), cost);

        return results;
    }

    public record IndexStats(int totalDocs, int uniqueTokens, double avgDocLength) {}

    // ========== 内部方法 ==========

    /**
     * 简化分词：支持英文单词切分 + 中文bigram
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<String> tokens = new ArrayList<>();
        String[] parts = text.split("[\\s\\p{Punct}]+");

        for (String part : parts) {
            if (part.isBlank()) continue;
            
            if (isAscii(part)) {
                tokens.add(part.toLowerCase());
            } else {
                for (int i = 0; i < part.length(); i++) {
                    tokens.add(String.valueOf(part.charAt(i)));
                    if (i < part.length() - 1) {
                        tokens.add(part.substring(i, i + 2));
                    }
                }
            }
        }
        return tokens;
    }

    private boolean isAscii(String s) {
        for (char c : s.toCharArray()) {
            if (c > 127) return false;
        }
        return true;
    }

    private String getDocKey(DocumentChunk doc) {
        if (doc.getChunkId() != null && !doc.getChunkId().isBlank()) {
            return doc.getChunkId();
        }
        return doc.getSource() + "_" + doc.getContent().hashCode();
    }

    private void recalcAvgDocLength() {
        if (docLengths.isEmpty()) {
            avgDocLength = 0;
        } else {
            avgDocLength = docLengths.values().stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0);
        }
    }

    // ========== 数据结构 ==========

    private static class TokenPosting {
        DocumentChunk doc;
        int termFrequency;

        TokenPosting(DocumentChunk doc, int tf) {
            this.doc = doc;
            this.termFrequency = tf;
        }
    }
}
