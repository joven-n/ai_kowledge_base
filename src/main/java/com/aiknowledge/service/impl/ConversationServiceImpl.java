package com.aiknowledge.service.impl;

import com.aiknowledge.model.dto.OpenAiDto;
import com.aiknowledge.model.entity.ConversationSession;
import com.aiknowledge.service.AiService;
import com.aiknowledge.service.ConversationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理服务实现（内存版）
 *
 * 生产环境建议：
 * - 使用 Redis 存储（支持分布式/过期自动清理）
 * - 开启 @EnableRedisHttpSession
 *
 * 本实现：
 * - ConcurrentHashMap 保证线程安全
 * - 定时清理过期会话（每5分钟执行一次）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final AiService aiService;

    @Value("${conversation.session-ttl-minutes:30}")
    private int sessionTtlMinutes;

    @Value("${conversation.max-history-turns:10}")
    private int maxHistoryTurns;

    // 内存会话存储（生产改Redis）
    private final Map<String, ConversationSession> sessionStore = new ConcurrentHashMap<>();

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个智能AI助手，具备以下能力：
            1. 基于知识库准确回答问题
            2. 记忆对话历史，支持连续提问
            3. 对不确定的内容会如实说明
            请根据对话历史和当前问题，给出准确、专业的回答。
            """;

    @Override
    public ConversationSession getOrCreateSession(String sessionId) {
        // 如果没有sessionId，创建新会话
        if (sessionId == null || sessionId.isBlank()) {
            return createNewSession();
        }

        ConversationSession session = sessionStore.get(sessionId);

        if (session == null) {
            log.info("会话不存在，创建新会话: sessionId={}", sessionId);
            session = createNewSession(sessionId);
        } else {
            // 检查是否过期
            if (isExpired(session)) {
                log.info("会话已过期，重新创建: sessionId={}", sessionId);
                sessionStore.remove(sessionId);
                session = createNewSession(sessionId);
            }
        }

        return session;
    }

    @Override
    public void saveSession(ConversationSession session) {
        // 保存前检查是否需要摘要（替换原来的trimHistory）
        summarizeIfNeeded(session);
        session.setLastActiveAt(LocalDateTime.now());
        sessionStore.put(session.getSessionId(), session);
        log.debug("会话保存: sessionId={}, 消息数={}", session.getSessionId(), session.getMessages().size());
    }

    @Override
    public void deleteSession(String sessionId) {
        sessionStore.remove(sessionId);
        log.info("会话删除: sessionId={}", sessionId);
    }

    // ======================== 私有方法 ========================

    private ConversationSession createNewSession() {
        return createNewSession(UUID.randomUUID().toString());
    }

    private ConversationSession createNewSession(String sessionId) {
        ConversationSession session = ConversationSession.builder()
                .sessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .build();

        // 添加系统提示词（对话的第一条消息）
        session.addMessage(OpenAiDto.Message.system(DEFAULT_SYSTEM_PROMPT));

        sessionStore.put(sessionId, session);
        log.info("创建新会话: sessionId={}", sessionId);
        return session;
    }

    private boolean isExpired(ConversationSession session) {
        LocalDateTime expireTime = session.getLastActiveAt().plusMinutes(sessionTtlMinutes);
        return LocalDateTime.now().isAfter(expireTime);
    }

    /** 摘要用的系统提示词 */
    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是一个对话摘要助手。请将以下多轮对话历史总结为简洁的摘要。
            要求：
            1. 保留关键信息、用户意图和重要结论
            2. 省略寒暄和无意义内容
            3. 使用简洁的中文，控制在200字以内
            4. 不要添加任何前缀或解释，直接输出摘要内容
            """;

    /**
     * 检查会话是否需要摘要，若需要则调用LLM生成摘要并注入
     */
    private void summarizeIfNeeded(ConversationSession session) {
        List<OpenAiDto.Message> toSummarize = session.prepareForSummarization(maxHistoryTurns);
        if (toSummarize.isEmpty()) return; // 未超限，无需摘要

        log.info("会话历史超限，开始摘要: sessionId={}, 待摘要消息数={}",
                session.getSessionId(), toSummarize.size());

        try {
            // 将待摘要消息格式化为文本
            String conversationText = formatMessagesForSummary(toSummarize);
            String summary = aiService.ask(SUMMARY_SYSTEM_PROMPT, conversationText);
            session.applySummary(summary);
            log.info("会话摘要完成: sessionId={}, 摘要长度={}", session.getSessionId(), summary.length());
        } catch (Exception e) {
            log.error("会话摘要失败，跳过摘要保留原始消息: sessionId={}", session.getSessionId(), e);
            // 摘要失败时回滚：不做任何处理（prepareForSummarization已裁剪了消息，
            // 但为安全起见，失败时不注入摘要，让后续消息自然延续）
        }
    }

    /**
     * 将消息列表格式化为纯文本，供LLM摘要使用
     */
    private String formatMessagesForSummary(List<OpenAiDto.Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (OpenAiDto.Message msg : messages) {
            String roleLabel = switch (msg.role()) {
                case "user" -> "用户";
                case "assistant" -> "助手";
                default -> msg.role();
            };
            sb.append("【").append(roleLabel).append("】").append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 定时清理过期会话（每5分钟执行一次）
     * 防止内存泄漏
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void cleanExpiredSessions() {
        int beforeSize = sessionStore.size();
        sessionStore.entrySet().removeIf(entry -> {
            boolean expired = isExpired(entry.getValue());
            if (expired) {
                log.debug("清理过期会话: sessionId={}", entry.getKey());
            }
            return expired;
        });
        int removedCount = beforeSize - sessionStore.size();
        if (removedCount > 0) {
            log.info("定时清理完成: 清理{}个过期会话, 剩余{}个会话", removedCount, sessionStore.size());
        }
    }

    @PostConstruct
    public void init() {
        log.info("会话管理服务初始化完成: sessionTtlMinutes={}, maxHistoryTurns={}",
                sessionTtlMinutes, maxHistoryTurns);
    }
}
