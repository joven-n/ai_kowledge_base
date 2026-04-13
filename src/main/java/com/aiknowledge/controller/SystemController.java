package com.aiknowledge.controller;

import com.aiknowledge.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统健康检查接口
 * 提供运行时状态监控
 */
@Slf4j
@RestController
@RequestMapping("/system")
public class SystemController {

    /**
     * 健康检查
     * GET /system/health
     *
     * 测试：
     * curl http://localhost:8080/system/health
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health(HttpServletRequest request) {
        Map<String, Object> info = new LinkedHashMap<>();

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

        info.put("status", "UP");
        info.put("service", "AI Knowledge Base RAG System");
        info.put("version", "1.0.0");
        info.put("time", LocalDateTime.now().toString());

        // 运行时间（毫秒转可读格式）
        long uptimeMs = runtimeBean.getUptime();
        info.put("uptime", formatUptime(uptimeMs));

        // 内存信息（返回字节数对象）
        Map<String, Long> memory = new LinkedHashMap<>();
        long usedBytes = memoryBean.getHeapMemoryUsage().getUsed();
        long maxBytes = memoryBean.getHeapMemoryUsage().getMax();
        memory.put("used", usedBytes);
        memory.put("total", maxBytes);
        memory.put("free", maxBytes - usedBytes);
        info.put("memory", memory);

        // 线程信息
        info.put("threads", threadBean.getThreadCount());

        // 可用处理器
        info.put("processors", Runtime.getRuntime().availableProcessors());

        return Result.success(info);
    }

    /**
     * API接口清单
     * GET /system/apis
     */
    @GetMapping("/apis")
    public Result<List<Map<String, String>>> apis() {
        List<Map<String, String>> apiList = new ArrayList<>();
        
        apiList.add(createApi("POST", "/ai/ask", "基础问答（直接调用大模型）"));
        apiList.add(createApi("POST", "/ai/rag", "RAG知识库问答"));
        apiList.add(createApi("POST", "/ai/chat", "多轮对话（带会话记忆）"));
        apiList.add(createApi("POST", "/ai/agent", "Agent工具调用"));
        apiList.add(createApi("POST", "/kb/upload", "上传文档（txt/pdf）到知识库"));
        apiList.add(createApi("POST", "/kb/text", "添加文本内容到知识库"));
        apiList.add(createApi("GET", "/kb/documents", "获取知识库文档列表"));
        apiList.add(createApi("GET", "/kb/stats", "知识库统计信息"));
        apiList.add(createApi("DELETE", "/kb/documents/{id}", "删除文档"));
        apiList.add(createApi("DELETE", "/ai/session/{id}", "删除对话会话"));
        apiList.add(createApi("GET", "/system/health", "系统健康检查"));
        
        return Result.success(apiList);
    }

    /**
     * 将毫秒格式化为可读运行时间
     */
    private String formatUptime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "天 " + (hours % 24) + "小时 " + (minutes % 60) + "分钟";
        }
        if (hours > 0) {
            return hours + "小时 " + (minutes % 60) + "分钟 " + (seconds % 60) + "秒";
        }
        if (minutes > 0) {
            return minutes + "分钟 " + (seconds % 60) + "秒";
        }
        return seconds + "秒";
    }

    /**
     * 创建API信息对象
     */
    private Map<String, String> createApi(String method, String path, String description) {
        Map<String, String> api = new LinkedHashMap<>();
        api.put("method", method);
        api.put("path", path);
        api.put("description", description);
        return api;
    }
}
