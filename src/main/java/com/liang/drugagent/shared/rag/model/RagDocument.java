package com.liang.drugagent.shared.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    /**
     * 法规发布日期（可选）
     */
    private LocalDate effectiveDate;

    /**
     * 法规层级（可选），如：国家法规、省级法规、市级法规
     */
    private String hierarchyLevel;

    /**
     * 法规状态（可选），如：有效、废止、修订中
     */
    private String status;

    /**
     * 主题标签列表（可选）
     */
    private List<String> topicTags;

    /**
     * 文档来源机构（可选）
     */
    private String sourceOrg;
}
