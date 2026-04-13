package com.aiknowledge.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具1：系统信息查询工具
 * 演示：查询当前时间、系统状态等
 */
@Slf4j
@Component
public class SystemInfoTool implements AgentTool {

    @Override
    public String getName() {
        return "system_info";
    }

    @Override
    public String getDescription() {
        return "查询系统信息，包括当前时间、服务状态等";
    }

    @Override
    public String getParameterDescription() {
        return "输入查询类型：'time'(当前时间) 或 'status'(系统状态)";
    }

    @Override
    public String execute(String input) {
        log.info("执行系统信息工具: input={}", input);

        try {
            String type = input.trim().toLowerCase();

            return switch (type) {
                case "time", "当前时间", "现在几点" -> {
                    String time = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    yield "当前时间：" + time;
                }
                case "status", "系统状态" -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("service", "AI知识库问答系统");
                    status.put("status", "运行中");
                    status.put("time", LocalDateTime.now().toString());
                    status.put("memory_mb", Runtime.getRuntime().totalMemory() / 1024 / 1024);
                    yield "系统状态：" + JSON.toJSONString(status);
                }
                default -> "当前时间：" + LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            };
        } catch (Exception e) {
            log.error("系统信息工具执行失败", e);
            return "工具执行失败：" + e.getMessage();
        }
    }
}
