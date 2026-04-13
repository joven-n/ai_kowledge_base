package com.aiknowledge.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档切片（Chunk）
 * RAG的基本检索单元
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    /** 切片唯一ID */
    private String chunkId;

    /** 所属文档ID */
    private String documentId;

    /** 文档来源（用于展示引用） */
    private String source;

    /** 切片文本内容 */
    private String content;

    /** 向量表示（用于相似度检索） */
    private float[] embedding;

    /** 切片在文档中的序号 */
    private int chunkIndex;

    /** 相似度分数（检索时填充） */
    private double score;

    /** 关键词检索得分（BM25，混合检索时填充） */
    private Double keywordScore;

    /** 重排序得分（Rerank阶段填充） */
    private Double rerankScore;
}
