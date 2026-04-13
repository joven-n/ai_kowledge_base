package com.aiknowledge.rag;

import com.aiknowledge.model.entity.DocumentChunk;

import java.util.List;

/**
 * 向量存储接口
 * 支持多种实现：内存版（开发/测试）、Milvus（生产）
 */
public interface VectorStore {

    /**
     * 添加文档切片（含向量）
     *
     * @param chunks 切片列表
     */
    void addChunks(List<DocumentChunk> chunks);

    /**
     * 相似度检索（TopK）
     *
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @return 按相似度降序排列的切片列表
     */
    List<DocumentChunk> search(float[] queryVector, int topK);

    /**
     * 按文档ID删除所有切片
     *
     * @param documentId 文档ID
     */
    void deleteByDocumentId(String documentId);

    /**
     * 获取存储中的切片总数
     */
    long count();
}
