package com.liang.drugagent.shared.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 文档。
 *
 * <p>表示待入库或已入库的文档。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocument {

    /**
     * 文档唯一标识
     */
    private String sourceId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 原始文本内容
     */
    private String rawText;

    /**
     * 文档类型，如 regulation、contract、case
     */
    private String docType;

    /**
     * 组织ID
     */
    private String orgId;

    /**
     * 业务场景
     */
    private String scene;

    /**
     * 子场景
     */
    private String subScene;

    /**
     * 切分后的 chunks
     */
    @Builder.Default
    private List<RagChunk> chunks = new ArrayList<>();

    /**
     * 文档创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 文档版本
     */
    private String version;
}
