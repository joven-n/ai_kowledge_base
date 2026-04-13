package com.aiknowledge.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切分器（Text Splitter）
 *
 * 策略：
 * 1. 按段落优先切分（保留语义完整性）
 * 2. 超长段落按句子切分
 * 3. 相邻块有重叠（overlap），保留上下文连贯性
 *
 * 这是RAG系统的核心组件之一，切分质量直接影响检索准确率
 */
@Slf4j
@Component
public class TextSplitter {

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap;

    /**
     * 将文本切分为多个Chunk
     *
     * @param text 原始文本
     * @return 切片列表
     */
    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 1. 清理文本
        text = text.replaceAll("\r\n", "\n")
                   .replaceAll("\r", "\n")
                   .replaceAll("\\n{3,}", "\n\n");  // 多个空行合并为两个

        List<String> chunks = new ArrayList<>();

        // 2. 先按段落切分（双换行为段落分隔符）
        String[] paragraphs = text.split("\n\n+");

        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;

            // 如果当前chunk加上新段落超过限制
            if (currentChunk.length() + paragraph.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                // 保留overlap：取上一个chunk的末尾部分
                String overlap = getOverlap(currentChunk.toString());
                currentChunk = new StringBuilder(overlap);
            }

            // 段落本身超过chunkSize，则按句子切分
            if (paragraph.length() > chunkSize) {
                List<String> sentenceChunks = splitBySentence(paragraph);
                for (String sc : sentenceChunks) {
                    if (currentChunk.length() + sc.length() > chunkSize && currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString().trim());
                        String overlap = getOverlap(currentChunk.toString());
                        currentChunk = new StringBuilder(overlap);
                    }
                    currentChunk.append(sc).append(" ");
                }
            } else {
                currentChunk.append(paragraph).append("\n\n");
            }
        }

        // 添加最后一个chunk
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        log.info("文本切分完成: 原始长度={}, 切片数量={}, chunkSize={}", text.length(), chunks.size(), chunkSize);
        return chunks;
    }

    /**
     * 按句子切分（处理超长段落）
     */
    private List<String> splitBySentence(String text) {
        List<String> result = new ArrayList<>();
        // 中英文句号、感叹号、问号作为句子边界
        String[] sentences = text.split("(?<=[。！？.!?])");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > chunkSize && current.length() > 0) {
                result.add(current.toString());
                current = new StringBuilder();
            }
            current.append(sentence);
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }

    /**
     * 获取用于overlap的文本片段（取末尾chunkOverlap个字符）
     */
    private String getOverlap(String text) {
        if (text.length() <= chunkOverlap) {
            return text;
        }
        return text.substring(text.length() - chunkOverlap);
    }
}
