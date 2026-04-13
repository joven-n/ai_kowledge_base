package com.aiknowledge.controller;

import com.aiknowledge.common.Result;
import com.aiknowledge.model.entity.KnowledgeDocument;
import com.aiknowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库管理接口
 * 文档上传、删除、查询
 */
@Slf4j
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 上传文档到知识库
     * POST /kb/upload
     *
     * 测试（上传txt文件）：
     * curl -X POST http://localhost:8080/kb/upload \
     *   -F "file=@/path/to/your/document.txt" \
     *   -F "docName=技术文档"
     *
     * 测试（上传pdf文件）：
     * curl -X POST http://localhost:8080/kb/upload \
     *   -F "file=@/path/to/your/document.pdf"
     */
    @PostMapping("/upload")
    public Result<KnowledgeDocument> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "docName", required = false) String docName) {

        log.info("文档上传请求: filename={}, size={}", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }

        KnowledgeDocument doc = knowledgeService.uploadDocument(file, docName);
        return Result.success("文档上传成功，已完成向量化处理", doc);
    }

    /**
     * 添加文本内容到知识库
     * POST /kb/text
     *
     * 测试：
     * curl -X POST http://localhost:8080/kb/text \
     *   -H "Content-Type: application/json" \
     *   -d '{"content":"RAG（检索增强生成）是一种结合检索和生成的AI技术...", "source":"技术博客"}'
     */
    @PostMapping("/text")
    public Result<KnowledgeDocument> addText(@RequestBody AddTextRequest request) {
        KnowledgeDocument doc = knowledgeService.addTextContent(request.content(), request.source());
        return Result.success("文本添加成功", doc);
    }

    /**
     * 获取知识库文档列表
     * GET /kb/documents
     */
    @GetMapping("/documents")
    public Result<List<KnowledgeDocument>> listDocuments() {
        return Result.success(knowledgeService.listDocuments());
    }

    /**
     * 获取知识库统计
     * GET /kb/stats
     */
    @GetMapping("/stats")
    public Result<KnowledgeService.KnowledgeStats> getStats() {
        return Result.success(knowledgeService.getStats());
    }

    /**
     * 删除文档
     * DELETE /kb/documents/{documentId}
     */
    @DeleteMapping("/documents/{documentId}")
    public Result<Void> deleteDocument(@PathVariable String documentId) {
        knowledgeService.deleteDocument(documentId);
        return Result.success(null);
    }

    record AddTextRequest(String content, String source) {}
}
