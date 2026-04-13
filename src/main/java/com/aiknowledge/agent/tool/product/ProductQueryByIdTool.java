package com.aiknowledge.agent.tool.product;

import com.aiknowledge.agent.tool.AgentTool;
import com.aiknowledge.model.entity.Product;
import com.aiknowledge.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 产品查询工具 - 根据产品ID查询单个产品的详细信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductQueryByIdTool implements AgentTool {

    private final ProductService productService;

    @Override
    public String getName() {
        return "product_query_by_id";
    }

    @Override
    public String getDescription() {
        return "根据产品ID查询产品的完整信息，包括名称、分类、价格、库存、状态、描述等。"
                + "适用于用户询问某个具体产品的详情、库存情况、价格等场景。"
                + "可查询的信息："
                + "- 基本信息：产品ID、产品名称、所属分类；"
                + "- 商业信息：售价（元）、当前库存数量；"
                + "- 状态信息：在售/缺货/下架；"
                + "- 详细描述：产品的文字介绍说明。"
                + "注意：如果输入中包含明确的产品ID格式（如 P001），优先使用此工具。";
    }

    @Override
    public String getParameterDescription() {
        return "{\"productId\": \"string (必填) - 产品ID，格式示例：P001, P002, P003\"}"
                + "\n\n输入示例："
                + "\n- \"P001\""
                + "\n- \"查询产品 P003\""
                + "\n- \"帮我看看这个产品：P005 的详情\"";
    }

    @Override
    public String execute(String input) {
        log.info("[ProductQueryById] 执行产品查询: input={}", input);
        try {
            String productId = extractProductId(input.trim());
            if (productId == null || productId.isBlank()) {
                return "错误：无法从输入中提取产品ID。请提供有效的产品ID（如 P001）。";
            }

            Optional<Product> productOpt = productService.findById(productId);
            if (productOpt.isEmpty()) {
                return String.format("未找到产品ID [%s] 对应的产品记录，请确认产品ID是否正确。", productId);
            }

            Product p = productOpt.get();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("╔════════════════════════════════════════╗%n"));
            sb.append(String.format("║             产品详情                   ║%n"));
            sb.append(String.format("╠════════════════════════════════════════╣%n"));
            sb.append(String.format("║  产品ID：%s%-29s║", p.getId(), ""));
            sb.append(String.format("%n║  名称  ：%-35s║", p.getName()));
            sb.append(String.format("%n║  分类  ：%-35s║", p.getCategory()));
            sb.append(String.format("%n║  价格  ：\u00A5%.2f%30s║", p.getPrice(), ""));
            sb.append(String.format("%n║  库存 ：%d 件%30s║", p.getStock(), ""));
            sb.append(String.format("%n║  状态  ：%-35s║", formatStatus(p.getStatus())));
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                String desc = p.getDescription().length() > 32 ? p.getDescription().substring(0, 32) + "..." : p.getDescription();
                sb.append(String.format("%n║  描述  ：%-35s║", desc));
            }
            sb.append(String.format("%n╚════════════════════════════════════════╝"));

            return sb.toString();

        } catch (Exception e) {
            log.error("[ProductQueryById] 查询失败", e);
            return "查询产品时发生错误：" + e.getMessage();
        }
    }

    private String extractProductId(String input) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(P\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(input.toUpperCase());
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (input.matches("\\d+")) {
            return "P" + input;
        }
        return input.length() <= 20 ? input : null;
    }

    private String formatStatus(String status) {
        return switch (status) {
            case "在售" -> "\u2705 在售";
            case "缺货" -> "\u26A0\uFE0F 缺货";
            case "下架" -> "\u274C 下架";
            default -> status;
        };
    }
}
