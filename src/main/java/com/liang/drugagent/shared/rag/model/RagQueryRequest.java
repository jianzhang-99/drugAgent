package com.liang.drugagent.shared.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 查询请求。
 *
 * <p>用于构建知识检索查询。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryRequest {

    /**
     * 用户问题
     */
    private String question;

    /**
     * 组织ID（必填，硬过滤）
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
     * 文档类型
     */
    private String docType;

    /**
     * 返回 top-k 结果，默认 5
     */
    @Builder.Default
    private Integer topK = 5;

    /**
     * 源文档ID过滤（可选）
     */
    private String sourceId;

    /**
     * 是否需要 LLM 生成回答，默认 true
     */
    @Builder.Default
    private Boolean needGenerateAnswer = true;

    /**
     * 会话 ID（用于追踪）
     */
    private String sessionId;
}
