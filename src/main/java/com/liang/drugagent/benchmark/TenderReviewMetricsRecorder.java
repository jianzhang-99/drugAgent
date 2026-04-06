package com.liang.drugagent.benchmark;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 标书审查评测指标记录器。
 *
 * <p>提供线程安全的指标记录能力，支持：
 * <ul>
 *   <li>LLM调用次数统计</li>
 *   <li>Token耗费统计</li>
 *   <li>各阶段耗时追踪</li>
 *   <li>规则命中记录</li>
 * </ul>
 *
 * <p>示例用法：
 * <pre>
 * // 开始记录
 * String traceId = metricsRecorder.startSession("session-123", 2);
 *
 * // 记录LLM调用
 * metricsRecorder.recordLlmCall(traceId, 1500, 300);
 *
 * // 记录规则命中
 * metricsRecorder.recordRuleHit(traceId, "W-M2", "联系方式近邻", "VERY_HIGH", 90, false);
 *
 * // 结束记录并获取报告
 * TenderReviewMetrics metrics = metricsRecorder.endSession(traceId, "HIGH", 85.0);
 * </pre>
 *
 * @author liangjiajian
 */
@Slf4j
@Component
public class TenderReviewMetricsRecorder {

    /** 线程安全的会话存储 */
    private final ConcurrentHashMap<String, SessionContext> sessions = new ConcurrentHashMap<>();

    /** 规则命中计数器 */
    private final AtomicLong totalRuleHits = new AtomicLong(0);

    /** LLM调用计数器 */
    private final AtomicLong totalLlmCalls = new AtomicLong(0);

    /** Token计数器 */
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);

    /**
     * 开始一个评测会话
     *
     * @param sessionId 会话ID
     * @param documentCount 文档数量
     * @return traceId 用于后续操作
     */
    public String startSession(String sessionId, int documentCount) {
        String traceId = sessionId + "_" + System.currentTimeMillis();
        SessionContext context = new SessionContext();
        context.sessionId = sessionId;
        context.documentCount = documentCount;
        context.startTime = System.currentTimeMillis();
        sessions.put(traceId, context);
        log.info("[评测指标] 开始会话 sessionId={}, documentCount={}, traceId={}",
                sessionId, documentCount, traceId);
        return traceId;
    }

    /**
     * 记录一次LLM调用
     *
     * @param traceId 追踪ID
     * @param inputTokens 输入Token数
     * @param outputTokens 输出Token数
     */
    public void recordLlmCall(String traceId, long inputTokens, long outputTokens) {
        SessionContext context = sessions.get(traceId);
        if (context != null) {
            context.llmCallCount.incrementAndGet();
            context.inputTokenCount.addAndGet(inputTokens);
            context.outputTokenCount.addAndGet(outputTokens);
        }
        totalLlmCalls.incrementAndGet();
        totalInputTokens.addAndGet(inputTokens);
        totalOutputTokens.addAndGet(outputTokens);
        log.debug("[评测指标] LLM调用 traceId={}, inputTokens={}, outputTokens={}",
                traceId, inputTokens, outputTokens);
    }

    /**
     * 记录一次规则命中
     *
     * @param traceId 追踪ID
     * @param ruleCode 规则编码
     * @param ruleName 规则名称
     * @param priority 优先级
     * @param weight 权重
     * @param exempted 是否豁免
     */
    public void recordRuleHit(String traceId, String ruleCode, String ruleName,
                              String priority, int weight, boolean exempted) {
        SessionContext context = sessions.get(traceId);
        if (context != null) {
            context.ruleHitCount.incrementAndGet();
            context.ruleHitRecords.add(
                    TenderReviewMetrics.RuleHitRecord.builder()
                            .ruleCode(ruleCode)
                            .ruleName(ruleName)
                            .priority(priority)
                            .weight(weight)
                            .exempted(exempted)
                            .build()
            );
        }
        totalRuleHits.incrementAndGet();
        log.debug("[评测指标] 规则命中 traceId={}, ruleCode={}, exempted={}",
                traceId, ruleCode, exempted);
    }

    /**
     * 记录豁免规则命中
     *
     * @param traceId 追踪ID
     */
    public void recordExemptionHit(String traceId) {
        SessionContext context = sessions.get(traceId);
        if (context != null) {
            context.exemptionHitCount.incrementAndGet();
        }
    }

    /**
     * 记录规则执行耗时
     *
     * @param traceId 追踪ID
     * @param timeMs 耗时（毫秒）
     */
    public void recordRuleExecutionTime(String traceId, long timeMs) {
        SessionContext context = sessions.get(traceId);
        if (context != null) {
            context.ruleExecutionTime.addAndGet(timeMs);
        }
    }

    /**
     * 记录LLM分析耗时
     *
     * @param traceId 追踪ID
     * @param timeMs 耗时（毫秒）
     */
    public void recordLlmAnalysisTime(String traceId, long timeMs) {
        SessionContext context = sessions.get(traceId);
        if (context != null) {
            context.llmAnalysisTime.addAndGet(timeMs);
        }
    }

    /**
     * 记录文档解析耗时
     *
     * @param traceId 追踪ID
     * @param timeMs 耗时（毫秒）
     */
    public void recordDocumentParsingTime(String traceId, long timeMs) {
        SessionContext context = sessions.get(traceId);
        if (context != null) {
            context.documentParsingTime.addAndGet(timeMs);
        }
    }

    /**
     * 记录报告生成耗时
     *
     * @param traceId 追踪ID
     * @param timeMs 耗时（毫秒）
     */
    public void recordReportGenerationTime(String traceId, long timeMs) {
        SessionContext context = sessions.get(traceId);
        if (context != null) {
            context.reportGenerationTime.addAndGet(timeMs);
        }
    }

    /**
     * 结束会话并获取评测报告
     *
     * @param traceId 追踪ID
     * @param riskLevel 风险等级
     * @param riskScore 综合评分
     * @return 评测指标
     */
    public TenderReviewMetrics endSession(String traceId, String riskLevel, Double riskScore) {
        SessionContext context = sessions.remove(traceId);
        if (context == null) {
            log.warn("[评测指标] 会话不存在 traceId={}", traceId);
            return null;
        }

        long totalTime = System.currentTimeMillis() - context.startTime;

        TenderReviewMetrics metrics = TenderReviewMetrics.builder()
                .sessionId(context.sessionId)
                .documentCount(context.documentCount)
                .ruleHitCount(context.ruleHitCount.get())
                .exemptionHitCount(context.exemptionHitCount.get())
                .llmCallCount(context.llmCallCount.get())
                .inputTokenCount(context.inputTokenCount.get())
                .outputTokenCount(context.outputTokenCount.get())
                .ruleExecutionTimeMs(context.ruleExecutionTime.get())
                .llmAnalysisTimeMs(context.llmAnalysisTime.get())
                .documentParsingTimeMs(context.documentParsingTime.get())
                .reportGenerationTimeMs(context.reportGenerationTime.get())
                .totalTimeMs(totalTime)
                .riskLevel(riskLevel)
                .riskScore(riskScore)
                .timestamp(System.currentTimeMillis())
                .ruleHitRecords(new ArrayList<>(context.ruleHitRecords))
                .build();

        log.info("[评测指标] 会话结束 sessionId={}, totalTime={}ms, ruleHits={}, llmCalls={}, tokens={}",
                context.sessionId, totalTime, metrics.getRuleHitCount(),
                metrics.getLlmCallCount(), metrics.getTotalTokenCount());

        return metrics;
    }

    /**
     * 获取全局累计统计
     */
    public GlobalStats getGlobalStats() {
        return GlobalStats.builder()
                .totalRuleHits(totalRuleHits.get())
                .totalLlmCalls(totalLlmCalls.get())
                .totalInputTokens(totalInputTokens.get())
                .totalOutputTokens(totalOutputTokens.get())
                .activeSessionCount(sessions.size())
                .build();
    }

    /**
     * 会话上下文
     */
    private static class SessionContext {
        String sessionId;
        int documentCount;
        long startTime;

        java.util.concurrent.atomic.AtomicInteger llmCallCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicLong inputTokenCount = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.concurrent.atomic.AtomicLong outputTokenCount = new java.util.concurrent.atomic.AtomicLong(0);

        java.util.concurrent.atomic.AtomicInteger ruleHitCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger exemptionHitCount = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.concurrent.atomic.AtomicLong ruleExecutionTime = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.concurrent.atomic.AtomicLong llmAnalysisTime = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.concurrent.atomic.AtomicLong documentParsingTime = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.concurrent.atomic.AtomicLong reportGenerationTime = new java.util.concurrent.atomic.AtomicLong(0);

        List<TenderReviewMetrics.RuleHitRecord> ruleHitRecords = new ArrayList<>();
    }

    /**
     * 全局累计统计
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GlobalStats {
        private long totalRuleHits;
        private long totalLlmCalls;
        private long totalInputTokens;
        private long totalOutputTokens;
        private int activeSessionCount;

        public long getTotalTokens() {
            return totalInputTokens + totalOutputTokens;
        }
    }
}
