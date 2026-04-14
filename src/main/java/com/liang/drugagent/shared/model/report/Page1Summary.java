package com.liang.drugagent.shared.model.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 审查结论总览（报告第1页）。
 *
 * <p>包含风险等级、审查结论、风险分、核心证据数量、命中规则数量、
 * 核心风险 Top3、风险分布及涉及文档等概要信息。</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Page1Summary {

    /** 风险等级：高/中/低 */
    private String riskLevel;

    /** 审查结论 */
    private String conclusion;

    /** 建议处置动作 */
    private String recommendedAction;

    /** 风险分 0-100 */
    private Integer riskScore;

    /** 核心证据数量 */
    private Integer coreEvidenceCount;

    /** 命中规则数量 */
    private Integer ruleHitCount;

    /** 核心风险 Top3 */
    private List<CoreRisk> coreRiskTop3;

    /** 风险分布 */
    private Map<String, String> riskDistribution;

    /** 涉及文档 */
    private List<DocumentInfo> documents;

    /**
     * 核心风险项。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoreRisk {
        /** 排名 */
        private Integer rank;
        /** 风险类型 */
        private String riskType;
        /** 标题 */
        private String title;
        /** 等级 */
        private String level;
        /** 摘要 */
        private String summary;
        /** 建议处置 */
        private String action;
    }

    /**
     * 文档信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentInfo {
        /** 文档ID */
        private String docId;
        /** 文档名称 */
        private String docName;
        /** 投标方 */
        private String party;
        /** 角色 */
        private String role;
        /** 内部ID */
        private String internalId;
    }
}
