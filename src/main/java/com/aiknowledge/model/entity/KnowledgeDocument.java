package com.aiknowledge.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识文档实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    /** 文档唯一ID */
    private String id;

    /** 文档名称 */
    private String name;

    /** 原始内容 */
    private String content;

    /** 文档来源（文件名/URL） */
    private String source;

    /** 上传时间 */
    private LocalDateTime createdAt;
}
