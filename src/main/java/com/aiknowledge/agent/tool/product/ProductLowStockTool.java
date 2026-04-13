package com.aiknowledge.agent.tool.product;

import com.aiknowledge.agent.tool.AgentTool;
import com.aiknowledge.model.entity.Product;
import com.aiknowledge.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 低库存预警工具 - 查询库存不足的产品，用于供应链管理提醒
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductLowStockTool implements AgentTool {

    private final ProductService productService;

    /** 默认低库存阈值 */
    private static final int DEFAULT_THRESHOLD = 10;

    @Override
    public String getName() {
        return "product_low_stock";
    }

    @Override
    public String getDescription() {
        return "查询库存低于指定阈值的产品列表，用于低库存预警和补货管理。\n"
                + "\n"
                + "功能特点：\n"
                + "- 自动识别库存紧张的产品（默认阈值10件）\n"
                + "- 支持自定义阈值（如\"库存低于5的\"）\n"
                + "- 展示每个低库存产品的当前库存数和差值\n"
                + "- 按库存数量升序排列，最紧急的在前面\n"
                + "\n"
                + "适用场景：\n"
                + "- \"哪些产品快没货了？\"\n"
                + "- \"库存低于10的产品\"\n"
                + "- \"需要补货的商品\"\n"
                + "- \"看看有没有缺货风险\"\n"
                + "\n"
                + "此工具专注于库存管理，不用于一般产品浏览。";
    }

    @Override
    public String getParameterDescription() {
        return "{\n"
                + "    \"threshold\": \"number (可选) - 库存阈值，默认为10。只返回库存低于此值的产品\"\n"
                + "}\n"
                + "\n"
                + "输入示例：\n"
                + "- \"低库存产品\"          （使用默认阈值10）\n"
                + "- \"库存低于5的\"         （阈值为5）\n"
                + "- \"threshold=20\"        （阈值为20）\n"
                + "- \"需要补货的产品\"      （使用默认阈值10）";
    }

    @Override
    public String execute(String input) {
        log.info("[ProductLowStock] 执行低库存查询: input={}", input);
        try {
            int threshold = parseThreshold(input.trim());

            List<Product> lowStockProducts = productService.findLowStockProducts(threshold);

            if (lowStockProducts.isEmpty()) {
                return String.format(
                    "✅ 当前所有产品库存充足（均高于阈值 %d），无需补货。%n" +
                    "建议关注日常销售趋势，提前规划采购。", threshold);
            }

            return formatLowStockReport(lowStockProducts, threshold);

        } catch (Exception e) {
            log.error("[ProductLowStock] 低库存查询失败", e);
            return "查询低库存产品时发生错误：" + e.getMessage();
        }
    }

    /**
     * 解析阈值参数
     */
    private int parseThreshold(String input) {
        // 尝试提取数字
        java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile("(\\d+)");
        java.util.regex.Matcher matcher = numPattern.matcher(input);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_THRESHOLD;
    }

    /**
     * 格式化低库存报告
     */
    private String formatLowStockReport(List<Product> products, int threshold) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("⚠️  低库存预警报告（阈值：< %d 件）%n%n", threshold));
        sb.append(String.format("┌──────┬──────────┬──────────┬───────┬────────┬──────────┐%n"));
        sb.append(String.format("│ ID   │ 名称      │ 分类     │ 当前库存│ 差值    │ 风险等级  │%n"));
        sb.append(String.format("├──────┼──────────┼──────────┼───────┼────────┼──────────┤%n"));

        for (Product p : products) {
            int gap = threshold - p.getStock();
            String riskLevel = assessRisk(p.getStock());
            sb.append(String.format("│ %-4s │ %-8s │ %-8s │  %3d件 │  -%-3d  │  %-8s│%n",
                    truncate(p.getId(), 4),
                    truncate(p.getName(), 8),
                    truncate(p.getCategory(), 8),
                    p.getStock(),
                    gap,
                    riskLevel));
        }

        sb.append(String.format("└──────┴──────────┴──────────┴───────┴────────┴──────────┘%n"));
        sb.append(String.format("%n共 %d 件产品库存低于阈值，请及时安排补货。%n", products.size()));

        // 统计缺货数量
        long outOfStock = products.stream()
                .filter(p -> p.getStock() == 0)
                .count();
        if (outOfStock > 0) {
            sb.append(String.format("🚨 其中 %d 件已完全缺货（库存为0）！%n", outOfStock));
        }

        return sb.toString();
    }

    /**
     * 评估风险等级
     */
    private String assessRisk(int stock) {
        if (stock == 0) return "🚨 缺货";
        if (stock <= 3) return "🔴 极高";
        if (stock <= 7) return "🟠 较高";
        return "🟡 一般";
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
