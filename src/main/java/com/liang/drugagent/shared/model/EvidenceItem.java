package com.liang.drugagent.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流证据项。
 *
 * <p>用于描述回答或分析结果背后的说明、来源和辅助信息。
 * 当证据来源为 RAG 时，应填充 ragChunkId 和 ragSourceId 以支持溯源。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceItem {

    /**
     * 证据标题
     */
    private String title;

    /**
     * 证据内容/片段
     */
    private String content;

    /**
     * 证据来源标识（仅用于兼容旧逻辑，建议优先使用 ragChunkId 和 ragSourceId）
     */
    @Deprecated
    private String source;

    /**
     * RAG Chunk ID（用于溯源）
     */
    private String ragChunkId;

    /**
     * RAG 源文档 ID（用于溯源）
     */
    private String ragSourceId;

    /**
     * RAG 相似度得分
     */
    private Float ragScore;

    /**
     * 兼容旧版三参数构造方式。
     */
    public EvidenceItem(String title, String content, String source) {
        this.title = title;
        this.content = content;
        this.source = source;
    }
}
