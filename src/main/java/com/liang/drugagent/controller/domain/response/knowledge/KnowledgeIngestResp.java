package com.liang.drugagent.controller.domain.response.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 入库响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIngestResp {

    /**
     * 文档来源ID（入库后生成）
     */
    private String sourceId;

    /**
     * 操作结果信息
     */
    private String message;
}