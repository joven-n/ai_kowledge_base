package com.aiknowledge.service;

import com.aiknowledge.model.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库服务接口
 * 负责文档上传、切分、向量化、存储
 */
public interface KnowledgeService {

    /**
     * 上传并处理文档（txt/pdf）
     *
     * @param file     上传的文件
     * @param docName  文档名称（可选，默认用文件名）
     * @return 文档信息
     */
    KnowledgeDocument uploadDocument(MultipartFile file, String docName);

    /**
     * 上传文本内容到知识库
     *
     * @param content 文本内容
     * @param source  来源标识
     * @return 文档信息
     */
    KnowledgeDocument addTextContent(String content, String source);

    /**
     * 删除文档
     *
     * @param documentId 文档ID
     */
    void deleteDocument(String documentId);

    /**
     * 获取所有文档列表
     */
    List<KnowledgeDocument> listDocuments();

    /**
     * 获取知识库统计信息
     */
    KnowledgeStats getStats();

    record KnowledgeStats(long documentCount, long chunkCount) {}
}
