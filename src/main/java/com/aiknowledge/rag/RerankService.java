package com.aiknowledge.rag;

import com.aiknowledge.model.entity.DocumentChunk;

import java.util.List;

/**
 * 重排序服务接口
 * 
 * 对检索结果进行精细化的相关性排序，提升最终答案质量。
 * 支持多种策略：
 * - Cross-Encoder：使用LLM判断问题-文档相关性（最准确）
 * - RRF融合排序：结合多路得分进行归一化融合
 * - 混合加权：向量得分 + 关键词得分的线性组合
 */
public interface RerankService {

    /**
     * 对文档列表进行重排序
     *
     * @param query   原始查询问题
     * @param docs    候选文档列表（已按初步相似度排序）
     * @param topK    最终返回的文档数量
     * @return 重排序后的文档列表
     */
    List<DocumentChunk> rerank(String query, List<DocumentChunk> docs, int topK);
}
