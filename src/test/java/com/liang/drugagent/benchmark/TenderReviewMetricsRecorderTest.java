package com.liang.drugagent.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TenderReviewMetricsRecorder 评测指标记录器单元测试。
 *
 * <p>测试场景：
 * <ol>
 *   <li>会话生命周期管理</li>
 *   <li>LLM调用次数统计</li>
 *   <li>Token耗费统计</li>
 *   <li>规则命中记录</li>
 *   <li>各阶段耗时追踪</li>
 *   <li>全局统计汇总</li>
 * </ol>
 */
class TenderReviewMetricsRecorderTest {

    private TenderReviewMetricsRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new TenderReviewMetricsRecorder();
    }

    /**
     * 测试：正常会话生命周期
     */
    @Test
    void should正常会话生命周期() {
        String traceId = recorder.startSession("session-1", 2);
        assertNotNull(traceId);

        recorder.recordLlmCall(traceId, 1500, 300);
        recorder.recordRuleHit(traceId, "W-M2", "联系方式近邻", "VERY_HIGH", 90, false);

        TenderReviewMetrics metrics = recorder.endSession(traceId, "HIGH", 85.0);

        assertNotNull(metrics);
        assertEquals("session-1", metrics.getSessionId());
        assertEquals(2, metrics.getDocumentCount());
        assertEquals(1, metrics.getLlmCallCount());
        assertEquals(1, metrics.getRuleHitCount());
        assertEquals(1500, metrics.getInputTokenCount());
        assertEquals(300, metrics.getOutputTokenCount());
        assertEquals("HIGH", metrics.getRiskLevel());
        assertEquals(85.0, metrics.getRiskScore());
    }

    /**
     * 测试：会话结束时返回null当会话不存在时
     */
    @Test
    void should会话不存在时返回Null() {
        TenderReviewMetrics metrics = recorder.endSession("non-existent", "HIGH", 85.0);
        assertNull(metrics);
    }

    /**
     * 测试：多次LLM调用累计统计
     */
    @Test
    void should多次Llm调用累计统计() {
        String traceId = recorder.startSession("session-2", 3);

        recorder.recordLlmCall(traceId, 1000, 200);
        recorder.recordLlmCall(traceId, 1500, 300);
        recorder.recordLlmCall(traceId, 2000, 400);

        TenderReviewMetrics metrics = recorder.endSession(traceId, "MEDIUM", 60.0);

        assertEquals(3, metrics.getLlmCallCount());
        assertEquals(4500, metrics.getInputTokenCount()); // 1000+1500+2000
        assertEquals(900, metrics.getOutputTokenCount()); // 200+300+400
        assertEquals(5400, metrics.getTotalTokenCount()); // 4500+900
    }

    /**
     * 测试：规则命中记录
     */
    @Test
    void should记录规则命中() {
        String traceId = recorder.startSession("session-3", 2);

        recorder.recordRuleHit(traceId, "W-M2", "联系方式近邻", "VERY_HIGH", 90, false);
        recorder.recordRuleHit(traceId, "W-M4", "版式模板同源", "MEDIUM", 70, true);
        recorder.recordExemptionHit(traceId);

        TenderReviewMetrics metrics = recorder.endSession(traceId, "HIGH", 80.0);

        assertEquals(2, metrics.getRuleHitCount());
        assertEquals(1, metrics.getExemptionHitCount());
        assertEquals(2, metrics.getRuleHitRecords().size());

        // 验证豁免标记
        assertTrue(metrics.getRuleHitRecords().stream()
                .anyMatch(r -> r.getRuleCode().equals("W-M4") && r.isExempted()));
    }

    /**
     * 测试：各阶段耗时追踪
     */
    @Test
    void should追踪各阶段耗时() {
        String traceId = recorder.startSession("session-4", 2);

        recorder.recordDocumentParsingTime(traceId, 150);
        recorder.recordRuleExecutionTime(traceId, 50);
        recorder.recordLlmAnalysisTime(traceId, 3000);
        recorder.recordReportGenerationTime(traceId, 100);

        TenderReviewMetrics metrics = recorder.endSession(traceId, "LOW", 30.0);

        assertEquals(150, metrics.getDocumentParsingTimeMs());
        assertEquals(50, metrics.getRuleExecutionTimeMs());
        assertEquals(3000, metrics.getLlmAnalysisTimeMs());
        assertEquals(100, metrics.getReportGenerationTimeMs());
    }

    /**
     * 测试：全局统计
     */
    @Test
    void should全局统计累计正确() {
        String traceId1 = recorder.startSession("session-5", 2);
        recorder.recordLlmCall(traceId1, 1000, 200);
        recorder.recordRuleHit(traceId1, "W-M2", "联系方式近邻", "VERY_HIGH", 90, false);
        recorder.endSession(traceId1, "HIGH", 85.0);

        String traceId2 = recorder.startSession("session-6", 3);
        recorder.recordLlmCall(traceId2, 2000, 400);
        recorder.recordRuleHit(traceId2, "W-M4", "版式模板同源", "MEDIUM", 70, false);
        recorder.endSession(traceId2, "MEDIUM", 55.0);

        TenderReviewMetricsRecorder.GlobalStats stats = recorder.getGlobalStats();

        assertEquals(2, stats.getTotalLlmCalls());
        assertEquals(2, stats.getTotalRuleHits());
        assertEquals(3000, stats.getTotalInputTokens()); // 1000+2000
        assertEquals(600, stats.getTotalOutputTokens()); // 200+400
        assertEquals(3600, stats.getTotalTokens());
        assertEquals(0, stats.getActiveSessionCount()); // 所有会话已结束
    }

    /**
     * 测试：Token总计数计算
     */
    @Test
    void shouldToken总计数计算正确() {
        String traceId = recorder.startSession("session-7", 2);

        recorder.recordLlmCall(traceId, 5000, 1000);

        TenderReviewMetrics metrics = recorder.endSession(traceId, "HIGH", 90.0);

        assertEquals(6000, metrics.getTotalTokenCount()); // 5000 + 1000
    }
}
