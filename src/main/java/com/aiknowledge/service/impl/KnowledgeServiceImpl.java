package com.aiknowledge.service.impl;

import com.aiknowledge.common.BusinessException;
import com.aiknowledge.model.entity.DocumentChunk;
import com.aiknowledge.model.entity.KnowledgeDocument;
import com.aiknowledge.rag.MarkdownSplitter;
import com.aiknowledge.rag.TextSplitter;
import com.aiknowledge.rag.VectorStore;
import com.aiknowledge.service.AiService;
import com.aiknowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库服务实现
 *
 * 核心流程：
 * 文档 → 文本提取 → 切分(Chunk) → Embedding向量化 → 向量存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final AiService aiService;
    private final VectorStore vectorStore;
    private final TextSplitter textSplitter;
    private final MarkdownSplitter markdownSplitter;

    // 文档元数据存储（生产环境改为数据库）
    private final Map<String, KnowledgeDocument> documentStore = new ConcurrentHashMap<>();

    @Override
    public KnowledgeDocument uploadDocument(MultipartFile file, String docName) {
        log.info("开始处理文档上传: filename={}", file.getOriginalFilename());

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String name = docName != null ? docName : filename;

        try {
            // 1. 提取文本
            String content = extractText(file, filename);

            if (content.isBlank()) {
                throw new BusinessException("文档内容为空，无法处理");
            }

            log.info("文本提取完成: filename={}, 字符数={}", filename, content.length());

            // 2. 创建文档记录
            String docId = UUID.randomUUID().toString();
            KnowledgeDocument document = KnowledgeDocument.builder()
                    .id(docId)
                    .name(name)
                    .content(content)
                    .source(filename)
                    .createdAt(LocalDateTime.now())
                    .build();

            // 3. 切分 + 向量化 + 存储
            processAndStore(document);

            documentStore.put(docId, document);
            log.info("文档处理完成: id={}, name={}", docId, name);
            return document;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档上传处理失败: filename={}", filename, e);
            throw new BusinessException("文档处理失败: " + e.getMessage());
        }
    }

    @Override
    public KnowledgeDocument addTextContent(String content, String source) {
        String docId = UUID.randomUUID().toString();
        KnowledgeDocument document = KnowledgeDocument.builder()
                .id(docId)
                .name(source)
                .content(content)
                .source(source)
                .createdAt(LocalDateTime.now())
                .build();

        processAndStore(document);
        documentStore.put(docId, document);

        log.info("文本内容添加到知识库: source={}, 字符数={}", source, content.length());
        return document;
    }

    @Override
    public void deleteDocument(String documentId) {
        vectorStore.deleteByDocumentId(documentId);
        documentStore.remove(documentId);
        log.info("文档已删除: documentId={}", documentId);
    }

    @Override
    public List<KnowledgeDocument> listDocuments() {
        return new ArrayList<>(documentStore.values());
    }

    @Override
    public KnowledgeStats getStats() {
        return new KnowledgeStats(documentStore.size(), vectorStore.count());
    }

    // ======================== 私有方法 ========================

    /**
     * 核心处理流程：切分 → Embedding → 存入向量库
     */
    private void processAndStore(KnowledgeDocument document) {
        log.info("📝 [开始处理] documentId={}, contentLength={}",
            document.getId(), document.getContent().length());

        try {
            // 1. 文本切分（根据文件类型选择切分器）
            List<String> chunks;
            String source = document.getSource().toLowerCase();
            if (source.endsWith(".md") || source.endsWith(".markdown")) {
                // Markdown文件使用专用切分器
                List<MarkdownSplitter.MarkdownChunk> mdChunks = markdownSplitter.split(document.getContent());
                chunks = mdChunks.stream()
                        .map(MarkdownSplitter.MarkdownChunk::getContent)
                        .toList();
                log.info("✂️ [Markdown切分完成] documentId={}, chunks={}", document.getId(), chunks.size());
            } else {
                // 其他文件使用通用文本切分器
                chunks = textSplitter.split(document.getContent());
                log.info("✂️ [文本切分完成] documentId={}, chunks={}", document.getId(), chunks.size());
            }

            if (chunks.isEmpty()) {
                log.warn("⚠️ [切分结果为空] documentId={}", document.getId());
                return;
            }

            // 2. 向量化（Embedding）
            List<DocumentChunk> documentChunks = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunkContent = chunks.get(i);
                log.info("🔤 [生成embedding] documentId={}, chunkIndex={}/{}, length={}",
                    document.getId(), i + 1, chunks.size(), chunkContent.length());

                long start = System.currentTimeMillis();
                float[] embedding = aiService.embedding(chunkContent);
                long cost = System.currentTimeMillis() - start;

                log.info("✅ [embedding成功] documentId={}, chunkIndex={}, dimension={}, cost={}ms",
                    document.getId(), i + 1, embedding.length, cost);

                DocumentChunk chunk = DocumentChunk.builder()
                        .chunkId(document.getId() + "_" + i)
                        .documentId(document.getId())
                        .source(document.getSource())
                        .content(chunkContent)
                        .embedding(embedding)
                        .chunkIndex(i)
                        .build();

                documentChunks.add(chunk);
            }

            // 3. 存入向量库
            log.info("💾 [准备存入向量库] documentId={}, chunks={}",
                document.getId(), documentChunks.size());
            vectorStore.addChunks(documentChunks);
            log.info("🎉 [存储完成] documentId={}, 向量库总量={}",
                document.getId(), vectorStore.count());

        } catch (Exception e) {
            log.error("❌ [处理失败] documentId={}, error={}",
                document.getId(), e.getMessage(), e);
            throw new BusinessException("文档向量化失败: " + e.getMessage());
        }
    }

    /**
     * 文本提取（支持 txt/pdf）
     */
    private String extractText(MultipartFile file, String filename) throws IOException {
        String lowerName = filename.toLowerCase();

        if (lowerName.endsWith(".pdf")) {
            return extractPdfText(file);
        } else if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
            return new String(file.getBytes());
        } else {
            // 默认尝试作为文本处理
            return new String(file.getBytes());
        }
    }

    private String extractPdfText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}
