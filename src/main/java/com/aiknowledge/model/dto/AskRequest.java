package com.aiknowledge.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 问答请求DTO
 */
@Data
public class AskRequest {

    @NotBlank(message = "问题不能为空")
    private String question;

    /** 会话ID（多轮对话时传入） */
    private String sessionId;

    /** 是否启用RAG（默认true） */
    private boolean useRag = true;

    /** 是否启用Agent（默认false） */
    private boolean useAgent = false;

    /** 系统提示词（可选，覆盖默认） */
    private String systemPrompt;
}
