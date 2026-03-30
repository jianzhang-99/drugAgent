package com.liang.drugagent.shared.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chunk 元数据。
 *
 * <p>描述每个知识片段的来源和位置信息。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkMetadata {

    /**
     * 组织ID，硬过滤条件
     */
    private String orgId;

    /**
     * 业务场景，如 tender_review、contract_precheck
     */
    private String scene;

    /**
     * 子场景
     */
    private String subScene;

    /**
     * 文档类型，如 regulation、contract、case
     */
    private String docType;

    /**
     * 源文档ID
     */
    private String sourceId;

    /**
     * 源文档标题
     */
    private String sourceTitle;

    /**
     * Chunk 唯一标识，格式：sourceId-index
     */
    private String chunkId;

    /**
     * Chunk 在文档中的顺序索引
     */
    private Integer chunkIndex;

    /**
     * 章节标题（可选）
     */
    private String sectionTitle;

    /**
     * 页码（可选）
     */
    private Integer pageNo;

    /**
     * 段落编号（可选）
     */
    private Integer paragraphNo;

    /**
     * 文档版本（可选）
     */
    private String version;
}
