package com.liang.drugagent.benchmark;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 标书审查评测基线指标记录。
 *
 * <p>记录每次标书审查的关键指标，用于追踪性能基线和迭代效果。
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderReviewMetrics {

    /** 评测会话唯一ID。 */
    private String sessionId;

    /** 文档数量。 */
    private int documentCount;

    /** 规则命中数量。 */
    private int ruleHitCount;

    /** 豁免规则命中数量。 */
    private int exemptionHitCount;

    /** LLM调用总次数。 */
    private int llmCallCount;

    /** 输入Token总耗费。 */
    private long inputTokenCount;

    /** 输出Token总耗费。 */
    private long outputTokenCount;

    /** 规则执行总耗时（毫秒）。 */
    private long ruleExecutionTimeMs;

    /** LLM分析总耗时（毫秒）。 */
    private long llmAnalysisTimeMs;

    /** 文档解析总耗时（毫秒）。 */
    private long documentParsingTimeMs;

    /** 报告生成总耗时（毫秒）。 */
    private long reportGenerationTimeMs;

    /** 总耗时（毫秒）。 */
    private long totalTimeMs;

    /** 风险等级：HIGH / MEDIUM / LOW。 */
    private String riskLevel;

    /** 综合风险评分。 */
    private Double riskScore;

    /** 评测时间戳。 */
    private long timestamp;

    /** 各规则命中明细。 */
    @Builder.Default
    private List<RuleHitRecord> ruleHitRecords = new ArrayList<>();

    /**
     * 单条规则命中记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleHitRecord {
        private String ruleCode;
        private String ruleName;
        private String priority;
        private int weight;
        private boolean exempted;
    }

    /**
     * 获取Token总耗费
     */
    public long getTotalTokenCount() {
        return inputTokenCount + outputTokenCount;
    }
}
