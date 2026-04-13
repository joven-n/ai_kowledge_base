package com.aiknowledge.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具2：外部API调用工具（天气查询示例）
 * 演示：调用外部REST API获取数据
 * 生产环境可替换为：CRM查询、数据库查询、内部API调用等
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherTool implements AgentTool {

    private final OkHttpClient okHttpClient;

    @Override
    public String getName() {
        return "weather_query";
    }

    @Override
    public String getDescription() {
        return "查询指定城市的天气信息";
    }

    @Override
    public String getParameterDescription() {
        return "输入城市名称，例如：北京、上海、深圳";
    }

    @Override
    public String execute(String input) {
        log.info("执行天气查询工具: city={}", input);

        try {
            String city = input.trim();

            // 这里使用免费的wttr.in API演示（无需Key）
            // 生产环境替换为付费天气API（如心知天气、和风天气）
            String url = "https://wttr.in/" + URLEncoder.encode(city, StandardCharsets.UTF_8) + "?format=j1&lang=zh";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "AI-Knowledge-Base/1.0")
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject weather = JSON.parseObject(body);

                    // 提取关键信息
                    JSONObject current = weather.getJSONArray("current_condition")
                            .getJSONObject(0);

                    String tempC = current.getString("temp_C");
                    String desc = current.getJSONArray("weatherDesc")
                            .getJSONObject(0).getString("value");
                    String humidity = current.getString("humidity");
                    String windSpeed = current.getString("windspeedKmph");

                    return String.format("【%s天气】温度：%s°C，天气：%s，湿度：%s%%，风速：%skm/h",
                            city, tempC, desc, humidity, windSpeed);
                } else {
                    return fallbackWeather(city);
                }
            }
        } catch (Exception e) {
            log.warn("天气查询API调用失败，使用模拟数据: {}", e.getMessage());
            return fallbackWeather(input);
        }
    }

    /**
     * 降级处理：返回模拟数据（避免网络问题导致Agent失败）
     */
    private String fallbackWeather(String city) {
        Map<String, String> mockWeather = new HashMap<>();
        mockWeather.put("北京", "晴，15°C，湿度45%，北风3级");
        mockWeather.put("上海", "多云，18°C，湿度65%，东风2级");
        mockWeather.put("深圳", "小雨，22°C，湿度80%，南风2级");
        mockWeather.put("广州", "阴，20°C，湿度75%，东南风2级");

        String weather = mockWeather.getOrDefault(city, "晴，20°C，湿度60%，微风");
        return String.format("【%s天气（模拟数据）】%s", city, weather);
    }
}
