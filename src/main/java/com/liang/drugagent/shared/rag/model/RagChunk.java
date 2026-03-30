package com.liang.drugagent.shared.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * RAG 知识片段。
 *
 * <p>表示从文档中切分出来的最小知识单元。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChunk {

    /**
     * Chunk 唯一标识
     */
    private String chunkId;

    /**
     * Chunk 内容文本
     */
    private String content;

    /**
     * 元数据
     */
    private ChunkMetadata metadata;

    /**
     * 向量嵌入（入库时使用）
     */
    private float[] embedding;

    /**
     * 检索相似度得分（查询时返回）
     */
    private Float score;

    /**
     * 额外属性
     */
    private Map<String, Object> extraAttrs;
}
