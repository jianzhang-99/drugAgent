package com.liang.drugagent.shared.rag.model;

/**
 * RAG 决策枚举。
 *
 * <p>描述检索结果的决策状态。</p>
 */
public enum RagDecision {

    /**
     * 已找到答案
     */
    ANSWERED,

    /**
     * 未命中，无相关候选
     */
    NO_HIT,

    /**
     * 需要人工审核
     */
    NEED_HUMAN_REVIEW
}
