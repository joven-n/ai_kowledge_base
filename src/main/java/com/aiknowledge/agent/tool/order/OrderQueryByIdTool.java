package com.aiknowledge.agent.tool.order;

import com.aiknowledge.agent.tool.AgentTool;
import com.aiknowledge.model.entity.Order;
import com.aiknowledge.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 订单查询工具 - 根据订单号查询单个订单的详细信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderQueryByIdTool implements AgentTool {

    private final OrderService orderService;

    @Override
    public String getName() {
        return "order_query_by_id";
    }

    @Override
    public String getDescription() {
        return "根据订单号查询订单的完整信息，包括客户姓名、购买产品、数量、金额、当前状态、物流单号等。"
                + "适用于用户询问某个具体订单的详情、物流状态、发货时间等场景。"
                + "可查询的信息包括："
                + "- 基本信息：订单号、客户名、产品名称、购买数量、总金额；"
                + "- 状态信息：当前状态（待付款/待发货/已发货/已完成/已取消）；"
                + "- 物流信息：物流单号（已发货后可见）、发货时间。"
                + "注意：如果输入中包含明确的订单号格式（如 O20240001），优先使用此工具。";
    }

    @Override
    public String getParameterDescription() {
        return "{\"orderId\": \"string (必填) - 订单号，格式示例：O20240001, O20240002\"}"
                + "\n\n输入示例："
                + "\n- \"O20240001\""
                + "\n- \"查询订单 O20240003 的状态\""
                + "\n- \"帮我查一下这个订单：O20240005\"";
    }

    @Override
    public String execute(String input) {
        log.info("[OrderQueryById] 执行订单查询: input={}", input);
        try {
            String orderId = extractOrderId(input.trim());
            if (orderId == null || orderId.isBlank()) {
                return "错误：无法从输入中提取订单号。请提供有效的订单号（如 O20240001）。";
            }

            Optional<Order> orderOpt = orderService.findById(orderId);
            if (orderOpt.isEmpty()) {
                return String.format("未找到订单号 [%s] 对应的订单记录，请确认订单号是否正确。", orderId);
            }

            Order o = orderOpt.get();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("╔══════════════════════════════════════╗%n"));
            sb.append(String.format("║           订单详情                   ║%n"));
            sb.append(String.format("╠══════════════════════════════════════╣%n"));
            sb.append(String.format("║  订单号：%s%-24s║", o.getId(), ""));
            sb.append(String.format("%n║  客户名：%-33s║", o.getCustomerName()));
            sb.append(String.format("%n║  产品名：%-33s║", o.getProductName()));
            sb.append(String.format("%n║  数量  ：%d件%29s║", o.getQuantity(), ""));
            sb.append(String.format("%n║  总金额：¥%.2f%26s║", o.getTotalAmount(), ""));
            sb.append(String.format("%n║  状态  ：%-33s║", formatStatus(o.getStatus())));
            if (o.getTrackingNo() != null) {
                sb.append(String.format("%n║  物流单号：%s%-23s║", o.getTrackingNo(), ""));
            }
            if (o.getShippedAt() != null) {
                sb.append(String.format("%n║  发货时间：%s%-22s║", o.getShippedAt().toLocalDate(), ""));
            }
            if (o.getCreatedAt() != null) {
                sb.append(String.format("%n║  下单时间：%s%-22s║", o.getCreatedAt().toLocalDate(), ""));
            }
            sb.append(String.format("%n╚══════════════════════════════════════╝"));

            return sb.toString();

        } catch (Exception e) {
            log.error("[OrderQueryById] 查询失败", e);
            return "查询订单时发生错误：" + e.getMessage();
        }
    }

    private String extractOrderId(String input) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(O\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(input.toUpperCase());
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (input.matches("\\d+")) {
            return "O" + input;
        }
        return input.length() <= 20 ? input : null;
    }

    private String formatStatus(String status) {
        return switch (status) {
            case "待付款" -> "\u23F3 待付款";
            case "待发货" -> "\uD83D\uDCE6 待发货";
            case "已发货" -> "\uD83D\uDE9A 已发货";
            case "已完成" -> "\u2705 已完成";
            case "已取消" -> "\u274C 已取消";
            default -> status;
        };
    }
}
