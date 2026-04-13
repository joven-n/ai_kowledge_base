package com.aiknowledge.agent.tool.product;

import com.aiknowledge.agent.tool.AgentTool;
import com.aiknowledge.model.entity.Product;
import com.aiknowledge.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 产品搜索工具 - 根据关键词、分类等条件搜索产品列表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchTool implements AgentTool {

    private final ProductService productService;

    /** 最大返回结果数 */
    private static final int MAX_RESULTS = 15;

    /** 已知的分类映射 */
    private static final String[][] CATEGORY_KEYWORDS = {
            {"数码配件", "数码", "配件"},
            {"电脑外设", "电脑", "外设"},
            {"显示设备", "显示", "显示器", "屏幕"},
            {"智能家居", "智能", "家居"},
            {"存储设备", "存储", "硬盘", "U盘", "SSD"},
            {"游戏配件", "游戏", "手柄"},
            {"摄影器材", "摄影", "相机"}
    };

    @Override
    public String getName() {
        return "product_search";
    }

    @Override
    public String getDescription() {
        return "根据关键词或分类搜索和筛选产品列表，帮助用户找到所需的产品。\n"
                + "支持多种搜索维度：\n"
                + "\n"
                + "1. 按关键词搜索：通过产品名模糊匹配（如\"耳机\"、\"键盘\"、\"蓝牙\"）\n"
                + "2. 按分类筛选：查看特定分类下的所有产品\n"
                + "3. 列出全部产品：不带条件时返回全部在售产品\n"
                + "\n"
                + "支持的产品分类：\n"
                + "- 数码配件（耳机、充电器等）\n"
                + "- 电脑外设（键盘、鼠标等）\n"
                + "- 显示设备（显示器等）\n"
                + "- 智能家居（智能音箱等）\n"
                + "- 存储设备（硬盘、U盘等）\n"
                + "- 游戏配件（手柄等）\n"
                + "- 摄影器材（相机等）\n"
                + "\n"
                + "典型使用场景：\n"
                + "- \"搜一下蓝牙耳机\"\n"
                + "- \"有哪些键盘？\"\n"
                + "- \"数码配件类的产品\"\n"
                + "- \"列出所有产品\"";
    }

    @Override
    public String getParameterDescription() {
        return "{\n"
            + "    \"keyword\": \"string (可选) - 产品名称关键词，如 '耳机'、'键盘'、'蓝牙'\",\n"
            + "    \"category\": \"string (可选) - 产品分类，可选值：数码配件 / 电脑外设 / 显示设备 / 智能家居 / 存储设备 / 游戏配件 / 摄影器材\"\n"
            + "}\n"
            + "\n"
            + "输入示例：\n"
            + "- \"蓝牙耳机\"\n"
            + "- \"category=数码配件\"\n"
            + "- \"机械键盘\"\n"
            + "- \"列出所有产品\"\n"
            + "- \"显示设备类产品有哪些\"\n"
            + "\n"
            + "注意：keyword 和 category 同时存在时优先按 category 搜索。";
    }

    @Override
    public String execute(String input) {
        log.info("[ProductSearch] 执行产品搜索: input={}", input);
        try {
            String query = input.trim();
            List<Product> products;
            String description;

            // 先检查是否匹配分类关键字
            String matchedCategory = matchCategory(query);

            if (matchedCategory != null) {
                products = productService.findByCategory(matchedCategory);
                description = String.format("分类【%s】的产品", matchedCategory);
            } else {
                // 尝试提取关键词
                String keyword = extractKeyword(query);
                if (keyword == null || keyword.isBlank()) {
                    products = productService.findAll();
                    description = "全部产品";
                } else {
                    products = productService.findByName(keyword);
                    description = String.format("关键词【%s】的搜索结果", keyword);

                    // 如果关键词没搜到结果，回退到全量展示
                    if (products.isEmpty()) {
                        products = productService.findAll();
                        description = String.format("关键词【%s】无匹配，展示全部产品", keyword);
                    }
                }
            }

            if (products.isEmpty()) {
                return String.format("未找到相关产品。（查询条件：%s）", description);
            }

            return formatProductTable(products, description);

        } catch (Exception e) {
            log.error("[ProductSearch] 搜索失败", e);
            return "搜索产品时发生错误：" + e.getMessage();
        }
    }

    /**
     * 匹配分类
     */
    private String matchCategory(String query) {
        String upperQuery = query.toUpperCase();
        for (String[] entry : CATEGORY_KEYWORDS) {
            for (String kw : entry) {
                if (upperQuery.contains(kw.toUpperCase())) {
                    return entry[0];
                }
            }
        }
        return null;
    }

    /**
     * 提取搜索关键词（去掉常见修饰词）
     */
    private String extractKeyword(String query) {
        String cleaned = query.replaceAll("(?i)产品|查询|搜索|查找|列出|有没有|哪些|什么|的?所有?", "").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    /**
     * 格式化为表格输出
     */
    private String formatProductTable(List<Product> products, String description) {
        int displayCount = Math.min(products.size(), MAX_RESULTS);
        boolean truncated = products.size() > MAX_RESULTS;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("┌──────────────────────────────────────────────────────┐%n"));
        sb.append(String.format("│  %s（共 %d 件）%-18s│%n",
                truncate(description, 26), products.size(), ""));
        sb.append(String.format("├──────┬──────────┬──────────┬───────┬──────┬──────┤%n"));
        sb.append(String.format("│ ID   │ 名称       │ 分类      │ 价格   │ 库存 │ 状态  │%n"));
        sb.append(String.format("├──────┼──────────┼──────────┼───────┼──────┼──────┤%n"));

        for (int i = 0; i < displayCount; i++) {
            Product p = products.get(i);
            sb.append(String.format("│ %-4s │ %-8s │ %-8s │ ¥%5s │ %4d │ %-6s│%n",
                    truncate(p.getId(), 4),
                    truncate(p.getName(), 8),
                    truncate(p.getCategory(), 8),
                    p.getPrice().toString(),
                    p.getStock(),
                    p.getStatus()));
        }

        sb.append(String.format("└──────┴──────────┴──────────┴───────┴──────┴──────┘%n"));

        if (truncated) {
            sb.append(String.format("（仅显示前 %d 条，共 %d 件产品）%n", MAX_RESULTS, products.size()));
        }

        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
