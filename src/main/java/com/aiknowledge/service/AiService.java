package com.aiknowledge.service;

import com.aiknowledge.model.dto.AskRequest;
import com.aiknowledge.model.dto.OpenAiDto;
import com.aiknowledge.model.entity.ConversationSession;
import com.aiknowledge.model.vo.AskResponse;

import java.util.List;

/**
 * AI调用服务接口
 * 封装大模型API调用，屏蔽底层实现细节
 * 支持接入 OpenAI / 通义千问 / 智谱 / SiliconFlow 等兼容接口
 */
public interface AiService {

    /**
     * 简单问答（无上下文）
     *
     * @param question 用户问题
     * @return AI回答
     */
    String ask(String question);

    /**
     * 带系统提示词的问答
     *
     * @param systemPrompt 系统提示词
     * @param question     用户问题
     * @return AI回答
     */
    String ask(String systemPrompt, String question);

    /**
     * 多轮对话（带历史消息）
     *
     * @param messages 对话历史（包含当前问题）
     * @return AI回答
     */
    String chat(List<OpenAiDto.Message> messages);

    /**
     * 文本向量化（Embedding）
     *
     * @param text 输入文本
     * @return 向量（float数组）
     */
    float[] embedding(String text);

    /**
     * 批量向量化
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    List<float[]> embeddingBatch(List<String> texts);

}
