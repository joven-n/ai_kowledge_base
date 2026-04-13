package com.aiknowledge.controller;

import com.aiknowledge.agent.AgentExecutor;
import com.aiknowledge.common.Result;
import com.aiknowledge.model.dto.AskRequest;
import com.aiknowledge.model.dto.OpenAiDto;
import com.aiknowledge.model.entity.ConversationSession;
import com.aiknowledge.model.vo.AskResponse;
import com.aiknowledge.rag.IntentDetector;
import com.aiknowledge.service.AiService;
import com.aiknowledge.service.ConversationService;
import com.aiknowledge.service.RagService;
import com.aiknowledge.util.IntentiUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 完整Chat接口（集成RAG + 多轮对话 + Agent）
 * - 多轮对话模式：自动检测意图，跨境电商问题走知识库检索+LLM，其他问题直接LLM
 * - Agent模式：保持原有工具决策逻辑不变
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class ChatController {

    private final AiService aiService;
    private final RagService ragService;
    private final ConversationService conversationService;
    private final AgentExecutor agentExecutor;
    private final IntentDetector intentDetector;
    private final IntentiUtil intentiUtil;

    /**
     * 多轮对话接口（集成意图检测 + RAG）
     * POST /ai/chat
     *
     * 流程：
     * 1. 意图检测（关键词匹配）
     * 2. CHAT类型 → 直接调用LLM回答
     * 3. CROSS_BORDER_QUERY → 检索知识库 → 整合上下文调用LLM
     */
    @PostMapping("/chat")
    public Result<AskResponse> chat(@Valid @RequestBody AskRequest request) {
        long start = System.currentTimeMillis();

        // 1. 意图检测
        IntentDetector.Intent intent = intentDetector.detect(request.getQuestion());
        log.info("多轮对话: question={}, 意图={}", request.getQuestion(), intent);

        // 2. 获取或创建会话
        ConversationSession session = conversationService.getOrCreateSession(request.getSessionId());

        // 3. 添加用户消息到会话历史
        session.addMessage(OpenAiDto.Message.user(request.getQuestion()));

        // 4. 根据意图分支处理
        String answer;
        if (intent == IntentDetector.Intent.CROSS_BORDER_QUERY) {
            answer = intentiUtil.handleKnowledgeQuery(request, session);
        } else {
            answer = intentiUtil.handleDirectChat(session);
        }

        // 5. 将AI回答加入历史
        session.addMessage(OpenAiDto.Message.assistant(answer));

        // 6. 保存会话（自动裁剪过长历史）
        conversationService.saveSession(session);

        AskResponse response = AskResponse.builder()
                .answer(answer)
                .sessionId(session.getSessionId())
                .costMs(System.currentTimeMillis() - start)
                .build();

        return Result.success(response);
    }

    /**
     * Agent工具调用接口
     * POST /ai/agent
     *
     * 测试（查询天气）：
     * curl -X POST http://localhost:8080/ai/agent \
     *   -H "Content-Type: application/json" \
     *   -d '{"question":"北京今天天气怎么样？","useAgent":true}'
     *
     * 测试（查询产品）：
     * curl -X POST http://localhost:8080/ai/agent \
     *   -H "Content-Type: application/json" \
     *   -d '{"question":"帮我查询产品P001的库存信息","useAgent":true}'
     */
    @PostMapping("/agent")
    public Result<AskResponse> agentAsk(@Valid @RequestBody AskRequest request) {
        log.info("Agent请求: question={}", request.getQuestion());
        AskResponse response = agentExecutor.execute(request);
        return Result.success(response);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        conversationService.deleteSession(sessionId);
        return Result.success(null);
    }
}
