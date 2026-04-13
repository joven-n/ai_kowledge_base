package com.aiknowledge.rag;

import com.aiknowledge.model.entity.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 内存向量存储实现
 *
 * 适用场景：开发测试、小规模知识库（<10000条）
 * 算法：余弦相似度（Cosine Similarity）
 *
 * 生产建议：替换为 Milvus / Pinecone / Weaviate
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "milvus.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {

    /**
     * 使用线程安全的List存储所有切片
     * 生产环境应改用Milvus等专业向量数据库
     */
    private final CopyOnWriteArrayList<DocumentChunk> chunks = new CopyOnWriteArrayList<>();

    @Override
    public void addChunks(List<DocumentChunk> newChunks) {
        chunks.addAll(newChunks);
        log.info("向量库新增切片: {}条, 当前总量: {}条", newChunks.size(), chunks.size());
    }

    @Override
    public List<DocumentChunk> search(float[] queryVector, int topK) {
        if (chunks.isEmpty()) {
            log.warn("向量库为空，无法检索");
            return List.of();
        }

        // 计算所有切片与查询向量的余弦相似度
        List<DocumentChunk> scored = chunks.stream()
                .filter(chunk -> chunk.getEmbedding() != null)
                .map(chunk -> {
                    double score = cosineSimilarity(queryVector, chunk.getEmbedding());
                    DocumentChunk copy = DocumentChunk.builder()
                            .chunkId(chunk.getChunkId())
                            .documentId(chunk.getDocumentId())
                            .source(chunk.getSource())
                            .content(chunk.getContent())
                            .embedding(chunk.getEmbedding())
                            .chunkIndex(chunk.getChunkIndex())
                            .score(score)
                            .build();
                    return copy;
                })
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))  // 降序
                .limit(topK)
                .collect(Collectors.toList());

        log.info("向量检索完成: topK={}, 最高分={}",
                scored.size(),
                scored.isEmpty() ? 0 : String.format("%.4f", scored.get(0).getScore()));

        return scored;
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        int before = chunks.size();
        chunks.removeIf(chunk -> documentId.equals(chunk.getDocumentId()));
        int removed = before - chunks.size();
        log.info("删除文档切片: documentId={}, 删除数量={}", documentId, removed);
    }

    @Override
    public long count() {
        return chunks.size();
    }

    /**
     * 余弦相似度计算
     * cos(θ) = (A·B) / (|A| × |B|)
     *
     * @param a 向量A
     * @param b 向量B
     * @return 相似度（-1到1之间，越接近1越相似）
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            log.warn("向量维度不匹配: {} vs {}", a.length, b.length);
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
