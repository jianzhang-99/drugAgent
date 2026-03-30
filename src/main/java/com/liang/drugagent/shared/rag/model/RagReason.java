package com.liang.drugagent.shared.rag.model;

/**
 * RAG 决策原因枚举。
 *
 * <p>描述决策的具体原因。</p>
 */
public enum RagReason {

    /**
     * 检索命中
     */
    HIT,

    /**
     * 无候选结果
     */
    NO_CANDIDATE,

    /**
     * 置信度低
     */
    LOW_CONFIDENCE,

    /**
     * 缺少上下文
     */
    MISSING_CONTEXT,

    /**
     * 上下文缺失（orgId 缺失等）
     */
    CONTEXT_MISSING
}
