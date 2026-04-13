package com.aiknowledge.controller;

import com.aiknowledge.common.Result;
import com.aiknowledge.model.dto.AskRequest;
import com.aiknowledge.model.vo.AskResponse;
import com.aiknowledge.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * AI 基础问答接口
 * STEP 1: /ai/ask - 直接调用大模型问答
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /**
     * 基础问答接口（无RAG，直接调用大模型）
     * POST /ai/ask
     *
     * 测试：
     * curl -X POST http://localhost:8080/ai/ask \
     *   -H "Content-Type: application/json" \
     *   -d '{"question":"什么是RAG技术？"}'
     */
    @PostMapping("/ask")
    public Result<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        long start = System.currentTimeMillis();
        log.info("收到问答请求: question={}", request.getQuestion());

        String answer = aiService.ask(request.getQuestion());

        AskResponse response = AskResponse.builder()
                .answer(answer)
                .model("gpt")
                .costMs(System.currentTimeMillis() - start)
                .build();

        return Result.success(response);
    }
}
