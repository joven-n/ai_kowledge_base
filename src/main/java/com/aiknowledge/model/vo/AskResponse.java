package com.aiknowledge.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 问答响应VO
 */
@Data
@Builder
public class AskResponse {

    /** AI回答内容 */
    private String answer;

    /** 会话ID（用于多轮对话） */
    private String sessionId;

    /** 引用的知识库文档（RAG场景） */
    private List<String> sources;

    /** 调用的工具列表（Agent场景） */
    private List<String> toolsCalled;

    /** 耗时（毫秒） */
    private long costMs;

    /** Token使用量 */
    private int tokensUsed;

    /** 使用的模型 */
    private String model;
}
