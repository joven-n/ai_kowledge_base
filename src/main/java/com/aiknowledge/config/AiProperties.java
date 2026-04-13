package com.aiknowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI API配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.api")
public class AiProperties {

    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey;
    private String chatModel = "gpt-3.5-turbo";
    private String embeddingModel = "text-embedding-ada-002";
    private int maxTokens = 2048;
    private double temperature = 0.7;
    private int timeoutSeconds = 60;
}
