package com.aiknowledge.service.impl;

import com.aiknowledge.common.BusinessException;
import com.aiknowledge.config.AiProperties;
import com.aiknowledge.model.dto.AskRequest;
import com.aiknowledge.model.dto.OpenAiDto;
import com.aiknowledge.model.entity.ConversationSession;
import com.aiknowledge.model.vo.AskResponse;
import com.aiknowledge.service.AiService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.milvus.param.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI调用服务实现（OpenAI兼容接口）
 * 支持：OpenAI / SiliconFlow / 智谱 / 通义千问（只需修改base-url和api-key）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final OkHttpClient okHttpClient;
    private final AiProperties aiProperties;

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    // ======================== 系统提示词 ========================
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个专业的AI知识库助手。
            请根据提供的上下文信息准确回答用户问题。
            如果上下文中没有相关信息，请诚实告知，不要编造内容。
            回答要简洁、准确、专业。
            """;

    @Override
    public String ask(String question) {
        return ask(DEFAULT_SYSTEM_PROMPT, question);
    }

    @Override
    public String ask(String systemPrompt, String question) {
        List<OpenAiDto.Message> messages = new ArrayList<>();
        messages.add(OpenAiDto.Message.system(systemPrompt));
        messages.add(OpenAiDto.Message.user(question));
        return chat(messages);
    }

    @Override
    public String chat(List<OpenAiDto.Message> messages) {
        long start = System.currentTimeMillis();
        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", aiProperties.getChatModel());
            requestBody.put("max_tokens", aiProperties.getMaxTokens());
            requestBody.put("temperature", aiProperties.getTemperature());

            // 加上这个参数关闭思考模式
            Map<String, Object> extraBody = new HashMap<>();
            extraBody.put("enable_thinking", false);   // ← 关键
            requestBody.put("extra_body", extraBody);

            JSONArray messagesArray = new JSONArray();
            for (OpenAiDto.Message msg : messages) {
                JSONObject msgObj = new JSONObject();
                msgObj.put("role", msg.role());
                msgObj.put("content", msg.content());
                messagesArray.add(msgObj);
            }
            requestBody.put("messages", messagesArray);

            String url = aiProperties.getBaseUrl() + "/chat/completions";
            String responseBody = doPost(url, requestBody.toJSONString());

            // 解析响应
            JSONObject response = JSON.parseObject(responseBody);
            String answer = response
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            long cost = System.currentTimeMillis() - start;
            log.info("AI Chat 完成，耗时: {}ms，模型: {}", cost, aiProperties.getChatModel());
            return answer;

        } catch (Exception e) {
            log.error("AI Chat 调用失败", e);
            throw new BusinessException("AI服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public float[] embedding(String text) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", aiProperties.getEmbeddingModel());
            requestBody.put("input", text);

            String url = aiProperties.getBaseUrl() + "/embeddings";
            String responseBody = doPost(url, requestBody.toJSONString());

            JSONObject response = JSON.parseObject(responseBody);
            JSONArray embeddingArray = response
                    .getJSONArray("data")
                    .getJSONObject(0)
                    .getJSONArray("embedding");

            float[] vector = new float[embeddingArray.size()];
            for (int i = 0; i < embeddingArray.size(); i++) {
                vector[i] = embeddingArray.getFloatValue(i);
            }
            log.debug("Embedding 完成，维度: {}", vector.length);
            return vector;

        } catch (Exception e) {
            log.error("Embedding 调用失败", e);
            throw new BusinessException("Embedding服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public List<float[]> embeddingBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        // 批量embedding（简单实现，可优化为真正批量请求）
        for (String text : texts) {
            results.add(embedding(text));
        }
        return results;
    }

    // ======================== 私有方法 ========================

    private String doPost(String url, String jsonBody) throws IOException {
        RequestBody body = RequestBody.create(jsonBody, JSON_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer " + aiProperties.getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();

        log.debug("AI API请求: {}", url);

        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("AI API响应错误: code={}, body={}", response.code(), responseBody);
                throw new BusinessException("AI API返回错误: " + response.code() + " - " + responseBody);
            }

            return responseBody;
        }
    }


}
