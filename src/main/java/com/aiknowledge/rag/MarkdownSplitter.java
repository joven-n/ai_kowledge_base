package com.aiknowledge.rag;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown专用文档切分器
 *
 * 策略：
 * 1. 保留Markdown结构语义（标题、代码块、表格）
 * 2. 按章节标题切分，将标题作为上下文注入chunk
 * 3. 代码块和表格作为独立整体，不被截断
 * 4. 支持多级标题层级追踪
 *
 * 解决的问题：
 * - 标题与正文分离导致上下文丢失
 * - 代码块被截断失去完整性
 * - 表格结构破坏无法阅读
 */
@Slf4j
@Component
public class MarkdownSplitter {

    @Value("${rag.markdown.chunk-size:800}")
    private int chunkSize;

    @Value("${rag.markdown.chunk-overlap:100}")
    private int chunkOverlap;

    // Markdown结构匹配模式
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\w]*\\n[\\s\\S]*?```", Pattern.MULTILINE);
    private static final Pattern TABLE_PATTERN = Pattern.compile("(^\\|.+\\|\\n?)+", Pattern.MULTILINE);

    /**
     * 将Markdown文本切分为多个Chunk（包含章节上下文）
     */
    public List<MarkdownChunk> split(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        // 1. 清理文本
        markdown = normalize(markdown);

        // 2. 解析Markdown结构为Block列表
        List<MarkdownBlock> blocks = parseMarkdownBlocks(markdown);

        // 3. 按策略切分为Chunks
        List<MarkdownChunk> chunks = processBlocks(blocks);

        log.info("Markdown切分完成: 原始长度={}, 切片数量={}, chunkSize={}",
                markdown.length(), chunks.size(), chunkSize);
        return chunks;
    }

    /**
     * 规范化Markdown文本
     */
    private String normalize(String text) {
        return text.replaceAll("\r\n", "\n")
                   .replaceAll("\r", "\n")
                   .replaceAll("\n{3,}", "\n\n");
    }

    /**
     * 解析Markdown文本为结构化Block列表
     */
    List<MarkdownBlock> parseMarkdownBlocks(String markdown) {
        List<MarkdownBlock> blocks = new ArrayList<>();

        // 先提取所有特殊结构的位置
        // 使用状态机方式解析

        String[] lines = markdown.split("\n");
        StringBuilder currentParagraph = new StringBuilder();
        boolean inCodeBlock = false;
        StringBuilder codeBuffer = new StringBuilder();

        for (String line : lines) {
            // 检测代码块开始/结束
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    // 代码块结束
                    codeBuffer.append(line).append("\n");
                    blocks.add(new MarkdownBlock(BlockType.CODE_BLOCK, codeBuffer.toString().trim()));
                    codeBuffer = new StringBuilder();
                    inCodeBlock = false;
                } else {
                    // 保存之前的段落
                    saveParagraph(currentParagraph, blocks);
                    currentParagraph = new StringBuilder();
                    inCodeBlock = true;
                    codeBuffer.append(line).append("\n");
                }
                continue;
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append("\n");
                continue;
            }

            // 检测标题
            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.find()) {
                saveParagraph(currentParagraph, blocks);
                currentParagraph = new StringBuilder();
                int level = headingMatcher.group(1).length();
                String title = headingMatcher.group(2).trim();
                blocks.add(new MarkdownBlock(BlockType.HEADING, title, level));
                continue;
            }

            // 检测表格行
            if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
                // 如果当前有段落内容，先保存
                if (currentParagraph.length() > 0 &&
                        !currentParagraph.toString().startsWith("|")) {
                    String paraText = currentParagraph.toString().trim();
                    if (!paraText.isEmpty() && !paraText.startsWith("|")) {
                        blocks.add(new MarkdownBlock(BlockType.PARAGRAPH, paraText));
                    }
                    currentParagraph = new StringBuilder();
                }
                currentParagraph.append(line).append("\n");
                continue;
            }

            // 表格后的非表格行，说明表格结束
            if (currentParagraph.length() > 0 &&
                    currentParagraph.toString().trim().startsWith("|") &&
                    !line.trim().startsWith("|")) {
                String tableText = currentParagraph.toString().trim();
                blocks.add(new MarkdownBlock(BlockType.TABLE, tableText));
                currentParagraph = new StringBuilder();
            }

            // 普通行
            currentParagraph.append(line).append("\n");
        }

        // 处理剩余内容
        if (inCodeBlock) {
            blocks.add(new MarkdownBlock(BlockType.CODE_BLOCK, codeBuffer.toString().trim()));
        } else if (currentParagraph.length() > 0) {
            String remaining = currentParagraph.toString().trim();
            if (!remaining.isEmpty()) {
                if (remaining.startsWith("|")) {
                    blocks.add(new MarkdownBlock(BlockType.TABLE, remaining));
                } else {
                    blocks.add(new MarkdownBlock(BlockType.PARAGRAPH, remaining));
                }
            }
        }

        return blocks;
    }

    /**
     * 保存段落到blocks
     */
    private void saveParagraph(StringBuilder buffer, List<MarkdownBlock> blocks) {
        String text = buffer.toString().trim();
        if (!text.isEmpty()) {
            blocks.add(new MarkdownBlock(BlockType.PARAGRAPH, text));
        }
    }

    /**
     * 将处理后的Blocks转换为最终的Chunks
     */
    List<MarkdownChunk> processBlocks(List<MarkdownBlock> blocks) {
        List<MarkdownChunk> chunks = new ArrayList<>();

        // 当前章节标题路径（支持多级标题）
        List<String> headingPath = new ArrayList<>();
        StringBuilder contentBuffer = new StringBuilder();
        int bufferStartIndex = 0;

        for (int i = 0; i < blocks.size(); i++) {
            MarkdownBlock block = blocks.get(i);

            switch (block.type) {
                case HEADING -> {
                    // 先保存之前积累的内容
                    if (contentBuffer.length() > 0) {
                        addChunk(chunks, contentBuffer, headingPath);
                        contentBuffer = new StringBuilder();
                    }

                    // 更新标题路径
                    updateHeadingPath(headingPath, block.level, block.content);
                }

                case CODE_BLOCK, TABLE -> {
                    // 先保存之前积累的内容
                    if (contentBuffer.length() > 0) {
                        addChunk(chunks, contentBuffer, headingPath);
                        contentBuffer = new StringBuilder();
                    }

                    // 代码块和表格作为独立chunk
                    String contextHeading = buildHeadingContext(headingPath);
                    String chunkContent;
                    if (contextHeading.isEmpty()) {
                        chunkContent = block.content;
                    } else {
                        chunkContent = "【" + contextHeading + "】\n\n" + block.content;
                    }
                    chunks.add(new MarkdownChunk(chunkContent, contextHeading,
                            block.type == BlockType.CODE_BLOCK ? ChunkType.CODE : ChunkType.TABLE));
                }

                case PARAGRAPH -> {
                    // 检查是否需要切分
                    String text = block.content + "\n\n";
                    if (contentBuffer.length() + text.length() > chunkSize
                            && contentBuffer.length() > chunkOverlap) {
                        // 需要切分
                        addChunk(chunks, contentBuffer, headingPath);
                        // 保留overlap
                        String overlap = getOverlap(contentBuffer.toString());
                        contentBuffer = new StringBuilder(overlap);
                    }
                    contentBuffer.append(text);
                }
            }
        }

        // 处理剩余内容
        if (contentBuffer.length() > 0) {
            addChunk(chunks, contentBuffer, headingPath);
        }

        return chunks;
    }

    /**
     * 添加chunk到结果列表
     */
    private void addChunk(List<MarkdownChunk> chunks, StringBuilder buffer, List<String> headingPath) {
        String content = buffer.toString().trim();
        if (!content.isEmpty()) {
            String contextHeading = buildHeadingContext(headingPath);
            String finalContent;
            if (contextHeading.isEmpty()) {
                finalContent = content;
            } else {
                finalContent = "【" + contextHeading + "】\n\n" + content;
            }
            chunks.add(new MarkdownChunk(finalContent, contextHeading, ChunkType.TEXT));
        }
    }

    /**
     * 更新标题路径（处理多级标题）
     */
    private void updateHeadingPath(List<String> path, int level, String title) {
        // 移除同级或更深层级的旧标题
        while (path.size() >= level) {
            path.remove(path.size() - 1);
        }
        path.add(title);
    }

    /**
     * 构建标题上下文字符串（如：第一章 > 第一节 > 小节）
     */
    private String buildHeadingContext(List<String> path) {
        if (path.isEmpty()) return "";
        return String.join(" > ", path);
    }

    /**
     * 获取用于overlap的文本片段
     */
    private String getOverlap(String text) {
        if (text.length() <= chunkOverlap) {
            return text;
        }
        // 尝试在句子边界处截断
        String substring = text.substring(text.length() - chunkOverlap);
        int sentenceEnd = substring.indexOf("。");
        if (sentenceEnd > 0 && sentenceEnd < substring.length() / 2) {
            return substring.substring(sentenceEnd + 1);
        }
        return substring;
    }

    // ======================== 内部数据结构 ========================

    /**
     * Block类型
     */
    enum BlockType {
        HEADING,       // 标题
        PARAGRAPH,     // 普通段落
        CODE_BLOCK,    // 代码块
        TABLE          // 表格
    }

    /**
     * Markdown结构化Block
     */
    @Data
    static class MarkdownBlock {
        BlockType type;
        String content;
        int level;  // 标题级别，仅HEADING类型使用

        MarkdownBlock(BlockType type, String content) {
            this.type = type;
            this.content = content;
            this.level = 0;
        }

        MarkdownBlock(BlockType type, String content, int level) {
            this.type = type;
            this.content = content;
            this.level = level;
        }
    }

    /**
     * 切分后的Chunk类型
     */
    public enum ChunkType {
        TEXT,   // 普通文本
        CODE,   // 代码块
        TABLE   // 表格
    }

    /**
     * Markdown切分结果Chunk
     */
    @Data
    public static class MarkdownChunk {
        private final String content;      // chunk内容（含标题上下文）
        private final String headingPath;  // 标题路径
        private final ChunkType type;      // 类型

        public MarkdownChunk(String content, String headingPath, ChunkType type) {
            this.content = content;
            this.headingPath = headingPath;
            this.type = type;
        }

        /**
         * 获取纯文本内容（不包含标题前缀）
         */
        public String getPureContent() {
            if (content.startsWith("【")) {
                int end = content.indexOf("】");
                if (end > 0 && content.charAt(end + 1) == '\n') {
                    return content.substring(end + 2).trim();
                }
            }
            return content;
        }
    }
}
