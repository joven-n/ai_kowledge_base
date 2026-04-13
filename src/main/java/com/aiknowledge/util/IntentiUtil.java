package com.aiknowledge.util;

import com.aiknowledge.model.dto.AskRequest;
import com.aiknowledge.model.entity.ConversationSession;
import com.aiknowledge.model.vo.AskResponse;
import com.aiknowledge.service.AiService;
import com.aiknowledge.service.RagService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author 86130
 * @description
 * @since 2026/4/11 13:23
 */
@Slf4j
@Component
@AllArgsConstructor
public class IntentiUtil {

        private final RagService ragService;
        private final AiService aiService;

    /**
     * 处理跨境电商知识库查询
     * 流程：检索知识库 → 有结果则整合prompt调LLM → 无结果则fallback直接LLM
     */
    public String handleKnowledgeQuery(AskRequest request, ConversationSession session) {
        log.info("检测到跨境电商查询，正在检索知识库...");

        try {
            // 调用RAG服务检索知识库
            AskRequest ragRequest = new AskRequest();
            ragRequest.setQuestion(request.getQuestion());
            ragRequest.setSessionId(session.getSessionId());
            AskResponse ragResponse = ragService.ragAsk(ragRequest);

            if (ragResponse != null && ragResponse.getAnswer() != null && !ragResponse.getAnswer().isBlank()) {
                log.info("知识库检索成功，返回答案");
                return ragResponse.getAnswer();
            }

            log.warn("知识库未检索到相关内容，回退到直接LLM回答");
        } catch (Exception e) {
            log.error("知识库检索异常，回退到直接LLM回答", e);
        }

        // Fallback：知识库无结果时，直接使用LLM回答
        return aiService.chat(session.getMessages());
    }

    /**
     * 处理普通闲聊/问候/通用问题（直接调用LLM）
     */
    public String handleDirectChat(ConversationSession session) {
        return aiService.chat(session.getMessages());
    }

}
