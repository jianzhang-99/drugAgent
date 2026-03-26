package com.liang.drugagent.controller.domain.response.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 问答响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAskResp {

    /** 回答内容 */
    private String answer;

    /** 决策结果 */
    private String decision;

    /** 决策原因 */
    private String reason;

    /** 风险等级 */
    private String riskLevel;

    /** 引用列表 */
    private List<Citation> citations = new ArrayList<>();

    /**
     * 引用信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {

        /** 来源ID */
        private String sourceId;

        /** 来源标题 */
        private String sourceTitle;

        /** 内容块ID */
        private String chunkId;

        /** 内容片段 */
        private String snippet;

        /** 相关度分数 */
        private Double score;
    }
}
