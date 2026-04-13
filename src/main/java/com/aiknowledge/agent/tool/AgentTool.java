package com.aiknowledge.agent.tool;

/**
 * Agent工具接口
 * 所有工具必须实现此接口（策略模式）
 *
 * 扩展方式：新增工具只需实现此接口并注入Spring容器
 */
public interface AgentTool {

    /**
     * 工具名称（唯一标识）
     */
    String getName();

    /**
     * 工具描述（供LLM理解功能）
     */
    String getDescription();

    /**
     * 参数描述（告知LLM如何传参）
     */
    String getParameterDescription();

    /**
     * 执行工具
     *
     * @param input 工具输入（自然语言或JSON字符串）
     * @return 工具执行结果
     */
    String execute(String input);
}
