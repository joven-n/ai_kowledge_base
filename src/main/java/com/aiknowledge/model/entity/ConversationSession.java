package com.aiknowledge.model.entity;

import com.aiknowledge.model.dto.OpenAiDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话会话实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSession {

    /** 会话ID */
    private String sessionId;

    /** 对话历史 */
    @Builder.Default
    private List<OpenAiDto.Message> messages = new ArrayList<>();

    /** 历史摘要（LLM生成的过往对话总结） */
    private String historySummary;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;

    /**
     * 添加消息
     */
    public void addMessage(OpenAiDto.Message message) {
        this.messages.add(message);
        this.lastActiveAt = LocalDateTime.now();
    }

    /**
     * 准备需要摘要的早期消息
     * 当非系统消息数超过 maxTurns*2 条时，提取最早的超额消息用于摘要，
     * 并从 messages 中移除这些消息。
     *
     * @param maxTurns 最大保留轮数（每轮=user+assistant两条消息）
     * @return 需要被摘要的历史消息列表，若不需要摘要则返回空列表
     */
    public List<OpenAiDto.Message> prepareForSummarization(int maxTurns) {
        if (messages.isEmpty()) return List.of();

        // 分离system消息和非系统消息
        List<OpenAiDto.Message> systemMessages = messages.stream()
                .filter(m -> "system".equals(m.role()))
                .toList();

        List<OpenAiDto.Message> nonSystemMessages = messages.stream()
                .filter(m -> !"system".equals(m.role()))
                .toList();

        int maxMessages = maxTurns * 2;
        if (nonSystemMessages.size() <= maxMessages) {
            return List.of(); // 未超限，不需要摘要
        }

        // 提取需要被摘要的早期消息（超出保留范围的）
        int trimCount = nonSystemMessages.size() - maxMessages;
        List<OpenAiDto.Message> toSummarize = nonSystemMessages.subList(0, trimCount);
        List<OpenAiDto.Message> toKeep = nonSystemMessages.subList(trimCount, nonSystemMessages.size());

        // 更新messages：只保留system消息 + 保留范围内的消息
        this.messages = new ArrayList<>();
        this.messages.addAll(systemMessages);
        this.messages.addAll(toKeep);

        return new ArrayList<>(toSummarize);
    }

    /**
     * 将LLM生成的摘要作为历史上下文注入到对话中
     * 摘要以系统消息形式插入在已有系统消息之后
     *
     * @param summary LLM生成的对话摘要文本
     */
    public void applySummary(String summary) {
        if (summary == null || summary.isBlank()) return;

        this.historySummary = summary;

        String summaryContent = "【过往对话摘要】\n" + summary + "\n请基于以上摘要和后续对话历史继续回答用户问题。";

        // 找到所有system消息的插入位置（在最后一个system消息之后）
        int insertIndex = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("system".equals(messages.get(i).role())) {
                insertIndex = i + 1;
                break;
            }
        }

        messages.add(insertIndex, OpenAiDto.Message.system(summaryContent));
    }
}
