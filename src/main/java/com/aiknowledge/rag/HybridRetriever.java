package com.aiknowledge.rag;

import com.aiknowledge.model.entity.DocumentChunk;

import java.util.List;

/**
 * 混合检索器接口
 *
 * 整合两种检索方式：
 * 1. 向量检索（Vector Search）：基于语义相似度的稠密检索
 * 2. 关键词检索（Keyword Search）：基于关键词匹配的稀疏检索
 *
 * 然后通过 RRF（Reciprocal Rank Fusion）算法融合两路结果，
 * 最终输出统一排序的文档列表。
 */
public interface HybridRetriever {

    /**
     * 混合检索
     *
     * @param query        原始查询文本
     * @param queryVector  查询向量（已embedding化）
     * @param topK         最终返回的候选数量
     * @return 融合排序后的文档列表
     */
    List<DocumentChunk> hybridSearch(String query, float[] queryVector, int topK);
}
