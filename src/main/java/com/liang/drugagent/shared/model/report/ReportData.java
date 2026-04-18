package com.liang.drugagent.shared.model.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审查决策报告数据（V2 用户视角版）。
 *
 * <p>参照"标书审查报告原型V1"设计，聚焦用户可理解的风险信息。</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportData {

    // ==================== V2 用户视角报告结构 ====================

    /** 1. 总体结论 */
    private ExecutiveSummary executiveSummary;

    /** 2. 风险总览 */
    private RiskOverview riskOverview;

    /** 3. 本次比对文件 */
    private java.util.List<DocumentIndex> documents;

    /** 4. 关键证据明细 */
    private java.util.List<EvidenceChain> evidences;

    /** 5. 处置建议 */
    private ActionPlan actionPlan;

    /** 6. 报告元信息 */
    private ReportMetadata metadata;

    // ==================== 旧版结构（保留兼容） ====================

    /** 第1页：审查结论总览（旧版） */
    @Deprecated
    private Page1Summary page1Summary;

    /** 第2页：风险总览（旧版） */
    @Deprecated
    private Page2RiskOverview page2RiskOverview;

    /** 第3页：核心证据（旧版） */
    @Deprecated
    private Page3CoreEvidence page3CoreEvidence;

    /** 第4页：详细比对（旧版） */
    @Deprecated
    private Page4DetailComparison page4DetailComparison;

    /** 第5页：处置建议（旧版） */
    @Deprecated
    private Page5ActionSuggestions page5ActionSuggestions;

    /** 第6页：附录（旧版） */
    @Deprecated
    private Page6Appendix page6Appendix;

    // ==================== V2 子类型定义 ====================

    /**
     * 1. 总体结论
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutiveSummary {
        /** 结论正文（人话，不是"高风险"） */
        private String conclusionText;
        /** 风险综合判定标签，如"重大围标风险" */
        private String riskLevelLabel;
        /** 3个业务指标 */
        private Metrics metrics;
        /** 审查说明（一句话） */
        private String reviewNote;
        /** 兼容旧版 */
        private String riskLevel;
        private Integer riskScore;
        private String overallConclusion;
        private String recommendedAction;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Metrics {
            private Integer effectiveHits;
            private Integer evidenceClusters;
            private Integer documentCount;
        }
    }

    /**
     * 2. 风险总览
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskOverview {
        /** 重点风险判断（最多3条） */
        private java.util.List<TopRisk> topRisks;
        /** 只展示"有发现"的风险方向 */
        private java.util.List<RiskDistribution> distributions;
    }

    /**
     * 重点风险判断
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopRisk {
        private Integer rank;
        /** 原型中的规则编码，如 W-M7、W-P1 */
        private String ruleCode;
        /** 风险名称，如"报价结构异常" */
        private String riskName;
        private String level;
        /** 风险说明 */
        private String riskDesc;
        /** 关键事实 */
        private String keyFact;
        /** 为什么需要复核 */
        private String whyReview;
        /** 建议动作 */
        private String action;
        /** 兼容旧版 */
        private String riskLevel;
        private String riskType;
        private String description;
    }

    /**
     * 风险分布
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskDistribution {
        private String riskType;
        private Boolean found;
        private String foundDescription;
        private Boolean needReview;
        private String needReviewText;
        private String brief;
        /** 兼容旧版 */
        private Integer hitCount;
        private String explanation;
        private String level;
    }

    /**
     * 3. 本次比对文件
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentIndex {
        /** 文档编号，如"文档 A"或"DOC-001" */
        private String docCode;
        /** 原型要求的文档ID，如"DOC-001" */
        private String docId;
        /** 投标主体，如"晟博云创" */
        private String partyName;
        /** 文件名，如"投标人A_晟博云创_W-M1测试标书.md" */
        private String fileName;
        /** 文档性质 */
        private String docNature;
        /** 最后修改人 */
        private String lastModifier;
        /** 角色描述 */
        private String role;
        /** 兼容旧版 */
        private String docRole;
        private String id;
    }

    /**
     * 4. 关键证据明细
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceChain {
        private String evidenceId;
        private String type;
        private String level;
        private String evidenceChainId;
        private String evidenceChainName;
        private String similarity;
        /** 证据指向 */
        private String sourceType;
        /** 文档A名称 */
        private String docAName;
        /** 文档B名称 */
        private String docBName;
        /** 文档A原文/数据 */
        private String docAContent;
        /** 文档B原文/数据 */
        private String docBContent;
        /** 对比发现 */
        private String comparisonFinding;
        /** AI判定逻辑说明 */
        private String aiJudgment;
        /** 复核建议 */
        private String reviewSuggestion;
        /** 兼容旧版 */
        private String title;
        private String summary;
    }

    /**
     * 5. 处置建议
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionPlan {
        /** 任务清单 */
        private java.util.List<TaskItem> tasks;
        /** 兼容旧版 */
        private java.util.List<String> level1Actions;
        private java.util.List<String> level2Actions;
        private java.util.List<String> level3Actions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskItem {
        private String priority;
        private String action;
        private String role;
        private String goal;
    }

    /**
     * 6. 报告元信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportMetadata {
        private String generatedAt;
        private String reviewScope;
        /** 原型中的 DOCUMENT ID */
        private String documentId;
        /** 兼容旧版 */
        private String taskId;
        private String reportId;
        private String traceId;
        private String projectTarget;
        private String reviewType;
        private String systemVersion;
    }
}
