package com.aiknowledge.agent.tool.order;

import com.aiknowledge.agent.tool.AgentTool;
import com.aiknowledge.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 订单状态统计工具 - 统计各状态订单数量，用于运营分析
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusStatsTool implements AgentTool {

    private final OrderService orderService;

    /** 所有可能的订单状态 */
    private static final String[] ALL_STATUSES = {"待付款", "待发货", "已发货", "已完成", "已取消"};

    @Override
    public String getName() {
        return "order_status_stats";
    }

    @Override
    public String getDescription() {
        return "统计并展示各状态订单的数量分布，用于运营分析和业务概览。\n"
                + "\n"
                + "提供以下维度的统计：\n"
                + "- 各状态订单数量（待付款、待发货、已发货、已完成、已取消）\n"
                + "- 总订单数汇总\n"
                + "- 各状态占比计算\n"
                + "- 关键指标预警（如大量待处理订单）\n"
                + "\n"
                + "适用场景：\n"
                + "- \"今天订单情况怎么样？\"\n"
                + "- \"有多少订单还没发货？\"\n"
                + "- \"订单整体状况概览\"\n"
                + "- \"待处理的订单多吗\"\n"
                + "\n"
                + "此工具不接收具体订单号或客户名，而是返回全局统计数据。";
    }

    @Override
    public String getParameterDescription() {
        return "无必需参数。工具返回所有状态的订单统计数据。\n"
                + "\n"
                + "输入示例：\n"
                + "- \"订单统计\"\n"
                + "- \"看看订单概况\"\n"
                + "- \"各状态订单数量\"\n"
                + "- \"\"（空输入也返回全量统计）";
    }

    @Override
    public String execute(String input) {
        log.info("[OrderStatusStats] 执行订单状态统计");
        try {
            long totalOrders = 0;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("╔═════════════════════════════════════════════╗%n"));
            sb.append(String.format("║           📊 订单状态统计报告              ║%n"));
            sb.append(String.format("╠═════════════════╦══════════╦══════════════╣%n"));
            sb.append(String.format("║      状态       ║   数量   ║     占比      ║%n"));
            sb.append(String.format("╠═════════════════╬══════════╬══════════════╣%n"));

            for (String status : ALL_STATUSES) {
                long count = orderService.countByStatus(status);
                totalOrders += count;
                double percentage = totalOrders > 0 ? (count * 100.0 / totalOrders) : 0;

                String bar = buildBar(percentage);
                sb.append(String.format("║  %-13s ║  %6d  ║  %5.1f%%  %s  ║%n",
                        status, count, percentage, bar));
            }

            sb.append(String.format("╠═════════════════╬══════════╬══════════════╣%n"));
            sb.append(String.format("║  %-13s ║  %6d  ║  100.0%%       ║%n", "总计", totalOrders));
            sb.append(String.format("╚═════════════════╩══════════╩══════════════╝%n"));

            // 预警提示
            if (totalOrders > 0) {
                long pendingPayment = orderService.countByStatus("待付款");
                long pendingShipment = orderService.countByStatus("待发货");
                long pendingTotal = pendingPayment + pendingShipment;

                if (pendingTotal > 0) {
                    double pendingRate = pendingTotal * 100.0 / totalOrders;
                    sb.append(String.format("%n⚠️  待处理订单提醒：%d 笔（%.1f%%）%n", pendingTotal, pendingRate));
                    if (pendingPayment > 0) {
                        sb.append(String.format("   · 待付款：%d 笔%n", pendingPayment));
                    }
                    if (pendingShipment > 0) {
                        sb.append(String.format("   · 待发货：%d 笔%n", pendingShipment));
                    }
                }
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("[OrderStatusStats] 统计失败", e);
            return "统计订单状态时发生错误：" + e.getMessage();
        }
    }

    /**
     * 构建简单的文本进度条
     */
    private String buildBar(double percentage) {
        int len = (int) (percentage / 5); // 每5%一个字符
        if (len > 10) len = 10;
        return "█".repeat(Math.max(0, len)) + "░".repeat(Math.max(0, 10 - len));
    }
}
