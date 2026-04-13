package com.aiknowledge.agent;

import com.aiknowledge.agent.tool.AgentTool;
import com.aiknowledge.model.dto.AskRequest;
import com.aiknowledge.model.dto.OpenAiDto;
import com.aiknowledge.model.vo.AskResponse;
import com.aiknowledge.service.AiService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent执行引擎
 *
 * 实现 ReAct (Reasoning + Acting) 模式：
 * 1. 接收用户问题
 * 2. LLM分析是否需要调用工具（Reasoning）
 * 3. 如需工具，解析工具名称和参数
 * 4. 执行工具获取结果（Acting）
 * 5. 将工具结果反馈给LLM生成最终答案
 *
 * 核心亮点（面试时可深聊）：
 * - 工具注册机制（Strategy模式 + Spring依赖注入）
 * - ReAct循环（支持多步推理）
 * - 工具调用结果再输入LLM（闭环设计）
 */
@Slf4j
@Component
public class AgentExecutor {

    private final AiService aiService;
    private final Map<String, AgentTool> toolMap;

    // 最大Agent循环次数（防止无限循环）
    private static final int MAX_ITERATIONS = 3;

    @Autowired
    public AgentExecutor(AiService aiService, List<AgentTool> tools) {
        this.aiService = aiService;
        // 将工具列表转为Map，方便按名称查找
        this.toolMap = tools.stream()
                .collect(Collectors.toMap(AgentTool::getName, Function.identity()));
        log.info("Agent工具注册完成: {}", toolMap.keySet());
    }

    /**
     * Agent执行入口
     */
    public AskResponse execute(AskRequest request) {
        long start = System.currentTimeMillis();
        List<String> toolsCalled = new ArrayList<>();

        log.info("Agent开始执行: question={}", request.getQuestion());

        // 构建工具描述（注入到System Prompt）
        String toolsDescription = buildToolsDescription();

        // 构建Agent系统提示词（ReAct格式）
        String systemPrompt = buildAgentSystemPrompt(toolsDescription);

        // 构建初始消息
        List<OpenAiDto.Message> messages = new ArrayList<>();
        messages.add(OpenAiDto.Message.system(systemPrompt));
        messages.add(OpenAiDto.Message.user(request.getQuestion()));

        String finalAnswer = null;

        // ReAct循环
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            log.info("Agent迭代 {}/{}", iteration + 1, MAX_ITERATIONS);

            // 调用LLM决策
            String llmResponse = aiService.chat(messages);
            log.debug("LLM响应: {}", llmResponse);

            // 解析LLM决策
            AgentDecision decision = parseDecision(llmResponse);

            if (decision.isUseTool()) {
                // 执行工具
                String toolName = decision.toolName();
                String toolInput = decision.toolInput();

                log.info("Agent调用工具: tool={}, input={}", toolName, toolInput);
                toolsCalled.add(toolName);

                AgentTool tool = toolMap.get(toolName);
                String toolResult;

                if (tool != null) {
                    toolResult = tool.execute(toolInput);
                    log.info("工具执行结果: {}", toolResult);
                } else {
                    toolResult = "工具[" + toolName + "]不存在，可用工具：" + toolMap.keySet();
                    log.warn("工具不存在: {}", toolName);
                }

                // 将工具结果加入对话历史
                messages.add(OpenAiDto.Message.assistant(llmResponse));
                messages.add(OpenAiDto.Message.user("工具执行结果：" + toolResult + "\n\n请根据以上工具结果，给出最终答案。"));

            } else {
                // LLM直接给出答案，结束循环
                finalAnswer = decision.answer();
                log.info("Agent完成推理，迭代次数={}", iteration + 1);
                break;
            }
        }

        if (finalAnswer == null) {
            // 超过最大迭代次数，取最后一次LLM响应
            finalAnswer = aiService.chat(messages);
            log.warn("Agent超过最大迭代次数，强制结束");
        }

        long cost = System.currentTimeMillis() - start;
        log.info("Agent执行完成: 耗时={}ms, 调用工具={}", cost, toolsCalled);

        return AskResponse.builder()
                .answer(finalAnswer)
                .toolsCalled(toolsCalled)
                .costMs(cost)
                .build();
    }

    /**
     * 构建工具描述字符串
     */
    private String buildToolsDescription() {
        StringBuilder sb = new StringBuilder();
        toolMap.forEach((name, tool) -> {
            sb.append(String.format("- 工具名: %s\n  功能: %s\n  参数: %s\n\n",
                    name, tool.getDescription(), tool.getParameterDescription()));
        });
        return sb.toString();
    }

    /**
     * 构建Agent系统提示词（ReAct格式）
     */
    private String buildAgentSystemPrompt(String toolsDescription) {
        return String.format("""
                你是一个智能AI Agent，具备推理和工具调用能力。
                
                【可用工具】
                %s
                
                【工作流程】
                分析用户问题，判断是否需要调用工具：
                
                如果需要调用工具，请严格按以下JSON格式回复（不要加其他内容）：
                {"action":"use_tool","tool_name":"工具名称","tool_input":"工具输入"}
                
                如果不需要工具，或已有足够信息，直接回答：
                {"action":"final_answer","answer":"你的完整回答"}
                
                【注意】
                - 只输出JSON，不要输出其他内容
                - 如果问题与工具无关，直接给出final_answer
                - 工具调用结果会自动反馈给你
                """, toolsDescription);
    }

    /**
     * 解析LLM决策（工具调用 or 直接回答）
     */
    private AgentDecision parseDecision(String llmResponse) {
        try {
            // 提取JSON部分（LLM有时会附带额外文字）
            String jsonStr = extractJson(llmResponse);
            JSONObject json = JSON.parseObject(jsonStr);

            String action = json.getString("action");

            if ("use_tool".equals(action)) {
                return AgentDecision.toolCall(
                        json.getString("tool_name"),
                        json.getString("tool_input")
                );
            } else {
                // final_answer 或无法解析时，取answer字段或原始响应
                String answer = json.containsKey("answer")
                        ? json.getString("answer")
                        : llmResponse;
                return AgentDecision.finalAnswer(answer);
            }
        } catch (Exception e) {
            // 解析失败，当作直接回答处理
            log.warn("LLM响应JSON解析失败，当作直接回答: {}", llmResponse);
            return AgentDecision.finalAnswer(llmResponse);
        }
    }

    /**
     * 从文本中提取JSON字符串
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * Agent决策结果
     */
    record AgentDecision(boolean isUseTool, String toolName, String toolInput, String answer) {
        static AgentDecision toolCall(String toolName, String toolInput) {
            return new AgentDecision(true, toolName, toolInput, null);
        }

        static AgentDecision finalAnswer(String answer) {
            return new AgentDecision(false, null, null, answer);
        }
    }
}
