package com.liang.drugagent.shared.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 查询响应。
 *
 * <p>返回检索结果和生成的回答。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryResponse {

    /**
     * 决策结果
     */
    private RagDecision decision;

    /**
     * 决策原因
     */
    private RagReason reason;

    /**
     * 生成的回答
     */
    private String answer;

    /**
     * 风险等级（如果有）
     */
    private String riskLevel;

    /**
     * 引用列表
     */
    @Builder.Default
    private List<RagCitation> citations = new ArrayList<>();

    /**
     * 检索到的证据片段
     */
    @Builder.Default
    private List<RagChunk> evidenceChunks = new ArrayList<>();

    /**
     * 创建成功响应
     */
    public static RagQueryResponse success(String answer, List<RagCitation> citations, List<RagChunk> chunks) {
        return RagQueryResponse.builder()
                .decision(RagDecision.ANSWERED)
                .reason(RagReason.HIT)
                .answer(answer)
                .citations(citations)
                .evidenceChunks(chunks)
                .build();
    }

    /**
     * 创建未命中响应
     */
    public static RagQueryResponse noHit(String reasonText) {
        return RagQueryResponse.builder()
                .decision(RagDecision.NO_HIT)
                .reason(RagReason.NO_CANDIDATE)
                .answer("根据现有知识库未找到相关信息。")
                .build();
    }

    /**
     * 创建需要人工审核响应
     */
    public static RagQueryResponse needHumanReview(RagReason reason, String answer) {
        return RagQueryResponse.builder()
                .decision(RagDecision.NEED_HUMAN_REVIEW)
                .reason(reason)
                .answer(answer)
                .build();
    }
}
