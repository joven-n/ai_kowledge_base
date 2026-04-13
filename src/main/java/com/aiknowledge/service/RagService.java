package com.aiknowledge.service;

import com.aiknowledge.model.dto.AskRequest;
import com.aiknowledge.model.vo.AskResponse;

/**
 * RAG服务接口
 * 检索增强生成（Retrieval-Augmented Generation）
 */
public interface RagService {

    /**
     * RAG问答
     * 流程：向量化问题 → 检索相关文档 → 构造Prompt → 调用LLM → 返回答案
     *
     * @param request 问答请求
     * @return 包含答案和引用来源的响应
     */
    AskResponse ragAsk(AskRequest request);
}
