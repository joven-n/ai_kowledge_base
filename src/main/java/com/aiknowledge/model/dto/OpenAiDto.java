package com.aiknowledge.model.dto;

import java.util.List;

/**
 * OpenAI Chat Completion 请求/响应模型
 */
public class OpenAiDto {

    /**
     * 对话消息
     */
    public record Message(String role, String content) {
        public static Message system(String content) {
            return new Message("system", content);
        }
        public static Message user(String content) {
            return new Message("user", content);
        }
        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }

    /**
     * Chat Completion 请求体
     */
    public record ChatRequest(
            String model,
            List<Message> messages,
            int max_tokens,
            double temperature
    ) {}

    /**
     * Embedding 请求体
     */
    public record EmbeddingRequest(
            String model,
            String input
    ) {}
}
