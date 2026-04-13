package com.aiknowledge.agent.tool.order;

import com.aiknowledge.agent.tool.AgentTool;
import com.aiknowledge.model.entity.Order;
import com.aiknowledge.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单搜索工具 - 根据客户名、订单状态等条件搜索订单列表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSearchTool implements AgentTool {

    private final OrderService orderService;

    /** 最大返回结果数 */
    private static final int MAX_RESULTS = 20;

    @Override
    public String getName() {
        return "order_search";
    }

    @Override
    public String getDescription() {
        return "根据客户姓名、订单状态等条件搜索和筛选订单列表。支持多种搜索维度：" +
                "1. 按客户姓名搜索：查找某个客户的所有订单历史；" +
                "2. 按订单状态筛选：查看特定状态的订单（待付款/待发货/已发货/已完成/已取消）；" +
                "3. 组合条件筛选：同时指定客户名和状态，精确匹配；" +
                "4. 列出全部订单：不带条件时返回所有订单。" +
                "典型使用场景：'查看张三的所有订单'、'有哪些待发货的订单？'、'李四有没有已完成的订单'、'列出最近所有的订单'。" +
                "返回结果以表格形式展示，包含订单号、客户、产品、数量、金额、状态等关键信息。";
    }

    @Override
    public String getParameterDescription() {
        return "{\n"
            + "    \"customerName\": \"string (可选) - 客户姓名，用于筛选某客户的订单\",\n"
            + "    \"status\": \"string (可选) - 订单状态，可选值：待付款 / 待发货 / 已发货 / 已完成 / 已取消\",\n"
            + "    \"keyword\": \"string (可选) - 通用关键词，自动匹配客户名或状态\"\n"
            + "}\n"
            + "\n"
            + "输入示例：\n"
            + "- \"张三的订单\"\n"
            + "- \"status=待发货\"\n"
            + "- \"李四 已完成的订单\"\n"
            + "- \"列出所有订单\"\n"
            + "- \"待付款的订单有哪些\"\n"
            + "\n"
            + "注意：如果同时提供了 customerName 和 status，将进行组合查询（AND 条件）。";
    }

    @Override
    public String execute(String input) {
        log.info("[OrderSearch] 执行订单搜索: input={}", input);
        try {
            String query = input.trim();
            List<Order> orders;
            String description;

            // 解析结构化参数
            SearchParams params = parseParams(query);

            if (params.customerName != null && params.status != null) {
                // 组合查询
                orders = orderService.findByCustomerAndStatus(params.customerName, params.status);
                description = String.format("客户【%s】状态为【%s】的订单", params.customerName, params.status);
            } else if (params.status != null) {
                // 仅按状态查询
                orders = orderService.findByStatus(params.status);
                description = String.format("状态为【%s】的订单", params.status);
            } else if (params.customerName != null) {
                // 仅按客户名查询
                orders = orderService.findByCustomerName(params.customerName);
                description = String.format("客户【%s】的订单", params.customerName);
            } else {
                // 全部查询
                orders = orderService.findAll();
                description = "全部订单";
            }

            if (orders.isEmpty()) {
                return String.format("未找到符合条件的订单。（查询条件：%s）", description);
            }

            return formatOrderTable(orders, description);

        } catch (Exception e) {
            log.error("[OrderSearch] 搜索失败", e);
            return "搜索订单时发生错误：" + e.getMessage();
        }
    }

    /**
     * 解析搜索参数
     */
    private SearchParams parseParams(String query) {
        SearchParams params = new SearchParams();
        String upperQuery = query.toUpperCase();

        // 检测状态关键字
        if (upperQuery.contains("待付款")) { params.status = "待付款"; }
        else if (upperQuery.contains("待发货")) { params.status = "待发货"; }
        else if (upperQuery.contains("已发货") && !upperQuery.contains("未发货")) { params.status = "已发货"; }
        else if (upperQuery.contains("已完成")) { params.status = "已完成"; }
        else if (upperQuery.contains("已取消")) { params.status = "已取消"; }

        // 尝试提取客户名（简单启发式：找中文人名）
        params.customerName = extractChineseName(query);

        return params;
    }

    /**
     * 提取中文人名
     */
    private String extractChineseName(String text) {
        // 常见姓氏前缀
        java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile(
            "[张李王赵刘陈杨黄周吴郑孙][\\u4e00-\\u9fa5]{1,2}"
        );
        java.util.regex.Matcher matcher = namePattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * 格式化为表格输出
     */
    private String formatOrderTable(List<Order> orders, String description) {
        int displayCount = Math.min(orders.size(), MAX_RESULTS);
        boolean truncated = orders.size() > MAX_RESULTS;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("┌─────────────────────────────────────────────────────────────┐%n"));
        sb.append(String.format("│  %s（共 %d 条）%-25s│%n",
                truncate(description, 30), orders.size(), ""));
        sb.append(String.format("├──────┬──────────┬──────────┬────┬─────────┬──────┤%n"));
        sb.append(String.format("│ 订单号   │ 客户      │ 产品       │ 数量│ 金额     │ 状态   │%n"));
        sb.append(String.format("├──────┼──────────┼──────────┼────┼─────────┼──────┤%n"));

        for (int i = 0; i < displayCount; i++) {
            Order o = orders.get(i);
            sb.append(String.format("│ %-6s │ %-8s │ %-8s │ %2d │ ¥%7s │ %-6s|%n",
                    truncate(o.getId(), 6),
                    truncate(o.getCustomerName(), 8),
                    truncate(o.getProductName(), 8),
                    o.getQuantity(),
                    o.getTotalAmount().toString(),
                    o.getStatus()));
        }

        sb.append(String.format("└──────┴──────────┴──────────┴────┴─────────┴──────┘%n"));

        if (truncated) {
            sb.append(String.format("（仅显示前 %d 条，共 %d 条结果）%n", MAX_RESULTS, orders.size()));
        }

        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    /**
     * 内部参数容器
     */
    private static class SearchParams {
        String customerName;
        String status;
    }
}
