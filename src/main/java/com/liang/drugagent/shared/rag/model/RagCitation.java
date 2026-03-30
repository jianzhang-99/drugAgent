package com.liang.drugagent.shared.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 引用信息。
 *
 * <p>描述回答中引用的知识来源。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagCitation {

    /**
     * Chunk ID
     */
    private String chunkId;

    /**
     * 源文档 ID
     */
    private String sourceId;

    /**
     * 源文档标题
     */
    private String sourceTitle;

    /**
     * 引用片段内容
     */
    private String snippet;

    /**
     * 相似度得分
     */
    private Float score;

    /**
     * 章节标题（可选）
     */
    private String sectionTitle;

    /**
     * 页码（可选）
     */
    private Integer pageNo;
}
