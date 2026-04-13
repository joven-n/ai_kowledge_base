package com.aiknowledge.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NewTextSplitter {

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap;

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("(?<=[.!? .!?])\\s*");
    
    private static final List<String> SEPARATORS = List.of(
        "\n\n",  // 段落分隔
        "\n",    // 换行
        ". ",    // 英文句 + 空格
        "。",    // 中文句号
        "! ",    // 英文感叹 + 空格
        "！",    // 中文感叹号
        "? ",    // 英文问号 + 空格
        "？",    // 中文问号
        "; ",    // 分号
        ",",     // 逗号
        " "      // 空格
    );

    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        text = normalizeText(text);
        
        List<String> chunks = new ArrayList<>();
        
        splitRecursive(text, "", chunks, 0);
        
        log.info("文本切分完成：原始长度={}, 切片数量={}, chunkSize={}", 
                 text.length(), chunks.size(), chunkSize);
        return chunks;
    }

    private String normalizeText(String text) {
        return text.replaceAll("\r\n", "\n")
                   .replaceAll("\r", "\n")
                   .replaceAll("\\n{3,}", "\n\n");
    }

    private void splitRecursive(String text, String separator, List<String> chunks, int depth) {
        if (text.isEmpty()) {
            return;
        }

        if (countTokens(text) <= chunkSize) {
            chunks.add(text.trim());
            return;
        }

        if (depth >= SEPARATORS.size()) {
            List<String> forcedChunk = forceSplit(text);
            chunks.addAll(forcedChunk);
            return;
        }

        String currentSeparator = SEPARATORS.get(depth);
        String[] parts = text.split(Pattern.quote(currentSeparator));

        if (parts.length <= 1) {
            splitRecursive(text, currentSeparator, chunks, depth + 1);
            return;
        }

        StringBuilder currentChunk = new StringBuilder();
        
        for (String part : parts) {
            if (part.isEmpty()) continue;

            String partWithSep = part + currentSeparator;
            int partTokenCount = countTokens(partWithSep);

            if (currentChunk.length() > 0 && 
                countTokens(currentChunk.toString()) + partTokenCount > chunkSize) {
                
                chunks.add(currentChunk.toString().trim());
                
                String overlap = getOverlapSmart(currentChunk.toString());
                currentChunk = new StringBuilder(overlap);
            }

            if (partTokenCount > chunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                
                splitRecursive(partWithSep, "", chunks, depth + 1);
            } else {
                currentChunk.append(partWithSep);
            }
        }

        if (currentChunk.length() > 0) {
            String lastChunk = currentChunk.toString().trim();
            if (!lastChunk.isEmpty()) {
                chunks.add(lastChunk);
            }
        }
    }

    private List<String> forceSplit(String text) {
        List<String> result = new ArrayList<>();
        int len = text.length();
        int step = Math.max(chunkSize / 2, 50);
        
        for (int i = 0; i < len; i += step) {
            int end = Math.min(i + step, len);
            String chunk = text.substring(i, end);
            
            if (i > 0 && chunkOverlap > 0) {
                int overlapStart = Math.max(0, i - chunkOverlap);
                String overlap = text.substring(overlapStart, i);
                chunk = overlap + chunk;
            }
            
            result.add(chunk.trim());
        }
        
        return result;
    }

    private int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int chineseChars = 0;
        int englishWords = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fa5') {
                chineseChars++;
            } else if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                continue;
            } else {
                englishWords++;
            }
        }
        
        return (chineseChars * 3 + englishWords) / 4;
    }

    private String getOverlapSmart(String text) {
        if (text.length() <= chunkOverlap) {
            return text;
        }

        int targetPos = text.length() - chunkOverlap;
        int searchStart = Math.max(0, targetPos - 20);
        
        String searchRegion = text.substring(searchStart, targetPos);
        
        int[] breakPoints = {
            searchRegion.lastIndexOf('.'),
            searchRegion.lastIndexOf('。'),
            searchRegion.lastIndexOf('!'),
            searchRegion.lastIndexOf('！'),
            searchRegion.lastIndexOf('?'),
            searchRegion.lastIndexOf('？'),
            searchRegion.lastIndexOf(','),
            searchRegion.lastIndexOf(','),
            searchRegion.lastIndexOf(' ')
        };
        
        int bestBreak = -1;
        for (int pos : breakPoints) {
            if (pos > bestBreak) {
                bestBreak = pos;
            }
        }
        
        if (bestBreak > 0) {
            return text.substring(searchStart + bestBreak + 1);
        }
        
        return text.substring(targetPos);
    }
}
