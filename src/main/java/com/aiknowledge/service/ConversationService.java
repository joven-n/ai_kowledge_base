package com.aiknowledge.service;

import com.aiknowledge.model.entity.ConversationSession;

/**
 * 会话管理服务
 * 支持多轮对话上下文维护
 */
public interface ConversationService {

    /**
     * 获取或创建会话
     *
     * @param sessionId 会话ID（为空则创建新会话）
     * @return 会话对象
     */
    ConversationSession getOrCreateSession(String sessionId);

    /**
     * 保存会话
     *
     * @param session 会话对象
     */
    void saveSession(ConversationSession session);

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     */
    void deleteSession(String sessionId);
}
