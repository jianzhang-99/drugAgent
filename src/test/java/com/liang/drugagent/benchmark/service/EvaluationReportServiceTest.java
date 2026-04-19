package com.liang.drugagent.benchmark.service;

import com.liang.drugagent.benchmark.entity.EvaluationPairResult;
import com.liang.drugagent.benchmark.entity.EvaluationTask;
import com.liang.drugagent.benchmark.mapper.EvaluationPairResultMapper;
import com.liang.drugagent.benchmark.mapper.EvaluationTaskMapper;
import com.liang.drugagent.benchmark.service.EvaluationReportService.EvaluationReport;
import com.liang.drugagent.benchmark.service.EvaluationReportService.LlmJudgeMetrics;
import com.liang.drugagent.benchmark.service.EvaluationReportService.RiskConsistencyMetrics;
import com.liang.drugagent.benchmark.service.EvaluationReportService.ScoreComparisonMetrics;
import com.liang.drugagent.benchmark.service.WinRateCalculator.WinRateStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * EvaluationReportService 评估报告生成服务单元测试。
 *
 * <p>测试场景：
 * <ol>
 *   <li>完整报告生成 - 有效taskId, 多对评测结果 -> 验证报告包含所有指标</li>
 *   <li>空结果处理 - taskId存在但无pairResults -> 验证指标为0或null</li>
 *   <li>任务不存在 - 无效taskId -> 验证返回null</li>
 *   <li>总体建议判定 - winRate>=0.5, avgScoreDelta>=0, riskConsistency>=0.6 -> PASS</li>
 *   <li>总体建议判定 - 风险一致性低 -> NEED_IMPROVEMENT</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class EvaluationReportServiceTest {

    @Mock
    private EvaluationTaskMapper taskMapper;

    @Mock
    private EvaluationPairResultMapper pairResultMapper;

    @Mock
    private WinRateCalculator winRateCalculator;

    @InjectMocks
    private EvaluationReportService evaluationReportService;

    private EvaluationTask testTask;
    private List<EvaluationPairResult> testPairResults;

    @BeforeEach
    void setUp() {
        // 初始化测试任务
        testTask = EvaluationTask.builder()
                .id(1L)
                .name("测试评测任务")
                .status("COMPLETED")
                .baselineModel("baseline-model-v1")
                .challengerModel("challenger-model-v1")
                .sceneType("TENDER_REVIEW")
                .totalCases(10)
                .completedCases(8)
                .build();

        // 初始化测试成对结果
        testPairResults = Arrays.asList(
                EvaluationPairResult.builder()
                        .id(1L)
                        .taskId(1L)
                        .caseId("case-001")
                        .caseDescription("标书A vs 标书B")
                        .baselineScore(85.0)
                        .challengerScore(88.0)
                        .baselineRiskLevel("MEDIUM")
                        .challengerRiskLevel("MEDIUM")
                        .winner("CHALLENGER")
                        .llmJudgeConfidence(0.85)
                        .build(),
                EvaluationPairResult.builder()
                        .id(2L)
                        .taskId(1L)
                        .caseId("case-002")
                        .caseDescription("标书C vs 标书D")
                        .baselineScore(90.0)
                        .challengerScore(87.0)
                        .baselineRiskLevel("HIGH")
                        .challengerRiskLevel("HIGH")
                        .winner("BASELINE")
                        .llmJudgeConfidence(0.75)
                        .build(),
                EvaluationPairResult.builder()
                        .id(3L)
                        .taskId(1L)
                        .caseId("case-003")
                        .caseDescription("标书E vs 标书F")
                        .baselineScore(78.0)
                        .challengerScore(78.0)
                        .baselineRiskLevel("LOW")
                        .challengerRiskLevel("LOW")
                        .winner("TIE")
                        .llmJudgeConfidence(0.90)
                        .build()
        );
    }

    /**
     * Case 1: 完整报告生成 - 有效taskId, 多对评测结果 -> 验证报告包含所有指标
     */
    @Test
    void should完整报告生成包含所有指标() {
        // given
        Long taskId = 1L;
        WinRateStats winRateStats = WinRateStats.builder()
                .taskId(taskId)
                .baselineModel("baseline-model-v1")
                .challengerModel("challenger-model-v1")
                .totalPairs(3)
                .baselineWins(1)
                .challengerWins(1)
                .ties(1)
                .challengerWinRate(0.5)
                .avgScoreDelta(0.0)
                .riskConsistency(1.0)
                .suggestion("PASS")
                .build();

        when(taskMapper.selectById(taskId)).thenReturn(testTask);
        when(pairResultMapper.selectList(any())).thenReturn(testPairResults);
        when(winRateCalculator.calculateWinRate(taskId)).thenReturn(winRateStats);

        // when
        EvaluationReport report = evaluationReportService.generateReport(taskId);

        // then
        assertNotNull(report, "报告不应为null");
        assertEquals(taskId, report.getTaskId(), "任务ID应匹配");
        assertEquals("测试评测任务", report.getTaskName(), "任务名称应匹配");
        assertEquals("baseline-model-v1", report.getBaselineModel(), "Baseline模型应匹配");
        assertEquals("challenger-model-v1", report.getChallengerModel(), "Challenger模型应匹配");
        assertEquals("TENDER_REVIEW", report.getSceneType(), "场景类型应匹配");
        assertEquals(10, report.getTotalCases(), "总case数应匹配");
        assertEquals(3, report.getCompletedCases(), "完成case数应匹配");
        assertEquals("COMPLETED", report.getStatus(), "任务状态应匹配");

        // 验证LLM评判指标
        LlmJudgeMetrics llmMetrics = report.getLlmJudgeMetrics();
        assertNotNull(llmMetrics, "LLM评判指标不应为null");
        assertEquals(3, llmMetrics.getTotalPairs(), "总成对数应为3");
        assertEquals(3, llmMetrics.getLlmJudgedPairs(), "LLM评判成对数应为3");
        assertTrue(llmMetrics.getAvgConfidence() > 0, "平均置信度应大于0");

        // 验证评分对比指标
        ScoreComparisonMetrics scoreMetrics = report.getScoreComparisonMetrics();
        assertNotNull(scoreMetrics, "评分对比指标不应为null");
        assertTrue(scoreMetrics.getAvgBaselineScore() > 0, "Baseline平均分应大于0");
        assertTrue(scoreMetrics.getAvgChallengerScore() > 0, "Challenger平均分应大于0");

        // 验证风险一致性指标
        RiskConsistencyMetrics riskMetrics = report.getRiskConsistencyMetrics();
        assertNotNull(riskMetrics, "风险一致性指标不应为null");
        assertEquals(3, riskMetrics.getTotalPairs(), "总成对数应为3");
        assertTrue(riskMetrics.getConsistencyRate() >= 0, "一致性比例应>=0");

        // 验证胜率统计
        assertNotNull(report.getWinRateStats(), "胜率统计不应为null");
        assertEquals(winRateStats.getChallengerWinRate(), report.getWinRateStats().getChallengerWinRate(), "Challenger胜率应匹配");
    }

    /**
     * Case 2: 空结果处理 - taskId存在但无pairResults -> 验证指标为0或null
     */
    @Test
    void should空结果时指标为0或null() {
        // given
        Long taskId = 2L;
        EvaluationTask emptyTask = EvaluationTask.builder()
                .id(taskId)
                .name("空结果任务")
                .status("COMPLETED")
                .baselineModel("baseline-model")
                .challengerModel("challenger-model")
                .sceneType("TENDER_REVIEW")
                .totalCases(5)
                .completedCases(0)
                .build();

        when(taskMapper.selectById(taskId)).thenReturn(emptyTask);
        when(pairResultMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(winRateCalculator.calculateWinRate(taskId)).thenReturn(null);

        // when
        EvaluationReport report = evaluationReportService.generateReport(taskId);

        // then
        assertNotNull(report, "报告不应为null");
        assertEquals(taskId, report.getTaskId(), "任务ID应匹配");
        assertEquals(0, report.getCompletedCases(), "完成case数应为0");

        // 验证LLM评判指标为空结果
        LlmJudgeMetrics llmMetrics = report.getLlmJudgeMetrics();
        assertNotNull(llmMetrics, "LLM评判指标不应为null");
        assertEquals(0, llmMetrics.getTotalPairs(), "总成对数应为0");
        assertEquals(0, llmMetrics.getLlmJudgedPairs(), "LLM评判成对数应为0");
        assertEquals(0.0, llmMetrics.getAvgConfidence(), "平均置信度应为0");

        // 验证评分对比指标
        ScoreComparisonMetrics scoreMetrics = report.getScoreComparisonMetrics();
        assertNotNull(scoreMetrics, "评分对比指标不应为null");
        assertEquals(0.0, scoreMetrics.getAvgBaselineScore(), "Baseline平均分应为0");
        assertEquals(0.0, scoreMetrics.getAvgChallengerScore(), "Challenger平均分应为0");

        // 验证风险一致性指标
        RiskConsistencyMetrics riskMetrics = report.getRiskConsistencyMetrics();
        assertNotNull(riskMetrics, "风险一致性指标不应为null");
        assertEquals(0, riskMetrics.getTotalPairs(), "总成对数应为0");
        assertEquals(0.0, riskMetrics.getConsistencyRate(), "一致性比例应为0");

        // 验证总体建议为INSUFFICIENT_DATA
        assertEquals("INSUFFICIENT_DATA", report.getOverallSuggestion(), "空结果时应返回INSUFFICIENT_DATA");
    }

    /**
     * Case 3: 任务不存在 - 无效taskId -> 验证返回null
     */
    @Test
    void should任务不存在时返回null() {
        // given
        Long invalidTaskId = 999L;
        when(taskMapper.selectById(invalidTaskId)).thenReturn(null);

        // when
        EvaluationReport report = evaluationReportService.generateReport(invalidTaskId);

        // then
        assertNull(report, "任务不存在时应返回null");
    }

    /**
     * Case 4: 总体建议判定 - winRate>=0.5, avgScoreDelta>=0, riskConsistency>=0.6 -> PASS
     */
    @Test
    void should胜率达标且风险一致性好时返回PASS() {
        // given
        Long taskId = 4L;
        EvaluationTask task = EvaluationTask.builder()
                .id(taskId)
                .name("达标任务")
                .status("COMPLETED")
                .baselineModel("baseline-model")
                .challengerModel("challenger-model")
                .sceneType("TENDER_REVIEW")
                .totalCases(10)
                .completedCases(10)
                .build();

        // Challenger胜率=0.6 (3胜2负), avgScoreDelta=5.0, riskConsistency=0.8 (>=0.6)
        WinRateStats winRateStats = WinRateStats.builder()
                .taskId(taskId)
                .baselineModel("baseline-model")
                .challengerModel("challenger-model")
                .totalPairs(5)
                .baselineWins(2)
                .challengerWins(3)
                .ties(0)
                .challengerWinRate(0.6)
                .avgScoreDelta(5.0)
                .riskConsistency(0.8)
                .suggestion("PASS")
                .build();

        List<EvaluationPairResult> results = Arrays.asList(
                EvaluationPairResult.builder()
                        .id(1L).taskId(taskId).caseId("case-1")
                        .baselineScore(80.0).challengerScore(85.0)
                        .baselineRiskLevel("MEDIUM").challengerRiskLevel("MEDIUM")
                        .winner("CHALLENGER").llmJudgeConfidence(0.8).build(),
                EvaluationPairResult.builder()
                        .id(2L).taskId(taskId).caseId("case-2")
                        .baselineScore(75.0).challengerScore(82.0)
                        .baselineRiskLevel("HIGH").challengerRiskLevel("HIGH")
                        .winner("CHALLENGER").llmJudgeConfidence(0.85).build(),
                EvaluationPairResult.builder()
                        .id(3L).taskId(taskId).caseId("case-3")
                        .baselineScore(90.0).challengerScore(88.0)
                        .baselineRiskLevel("LOW").challengerRiskLevel("LOW")
                        .winner("BASELINE").llmJudgeConfidence(0.7).build(),
                EvaluationPairResult.builder()
                        .id(4L).taskId(taskId).caseId("case-4")
                        .baselineScore(70.0).challengerScore(78.0)
                        .baselineRiskLevel("MEDIUM").challengerRiskLevel("MEDIUM")
                        .winner("CHALLENGER").llmJudgeConfidence(0.9).build(),
                EvaluationPairResult.builder()
                        .id(5L).taskId(taskId).caseId("case-5")
                        .baselineScore(85.0).challengerScore(80.0)
                        .baselineRiskLevel("HIGH").challengerRiskLevel("HIGH")
                        .winner("BASELINE").llmJudgeConfidence(0.75).build()
        );

        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(pairResultMapper.selectList(any())).thenReturn(results);
        when(winRateCalculator.calculateWinRate(taskId)).thenReturn(winRateStats);

        // when
        EvaluationReport report = evaluationReportService.generateReport(taskId);

        // then
        assertNotNull(report, "报告不应为null");
        assertEquals("PASS", report.getOverallSuggestion(), "胜率达标且风险一致性>=0.6时应返回PASS");
    }

    /**
     * Case 5: 总体建议判定 - 风险一致性低 -> NEED_IMPROVEMENT
     */
    @Test
    void should风险一致性低时返回NEED_IMPROVEMENT() {
        // given
        Long taskId = 5L;
        EvaluationTask task = EvaluationTask.builder()
                .id(taskId)
                .name("风险不一致任务")
                .status("COMPLETED")
                .baselineModel("baseline-model")
                .challengerModel("challenger-model")
                .sceneType("TENDER_REVIEW")
                .totalCases(10)
                .completedCases(10)
                .build();

        // Challenger胜率=0.6 (3胜2负), avgScoreDelta=5.0, 但riskConsistency=0.4 (<0.6)
        WinRateStats winRateStats = WinRateStats.builder()
                .taskId(taskId)
                .baselineModel("baseline-model")
                .challengerModel("challenger-model")
                .totalPairs(5)
                .baselineWins(2)
                .challengerWins(3)
                .ties(0)
                .challengerWinRate(0.6)
                .avgScoreDelta(5.0)
                .riskConsistency(0.4)
                .suggestion("PASS")
                .build();

        // 风险等级不一致的成对结果
        List<EvaluationPairResult> results = Arrays.asList(
                EvaluationPairResult.builder()
                        .id(1L).taskId(taskId).caseId("case-1")
                        .baselineScore(80.0).challengerScore(85.0)
                        .baselineRiskLevel("MEDIUM").challengerRiskLevel("HIGH")  // 不一致
                        .winner("CHALLENGER").llmJudgeConfidence(0.8).build(),
                EvaluationPairResult.builder()
                        .id(2L).taskId(taskId).caseId("case-2")
                        .baselineScore(75.0).challengerScore(82.0)
                        .baselineRiskLevel("HIGH").challengerRiskLevel("MEDIUM")  // 不一致
                        .winner("CHALLENGER").llmJudgeConfidence(0.85).build(),
                EvaluationPairResult.builder()
                        .id(3L).taskId(taskId).caseId("case-3")
                        .baselineScore(90.0).challengerScore(88.0)
                        .baselineRiskLevel("LOW").challengerRiskLevel("LOW")  // 一致
                        .winner("BASELINE").llmJudgeConfidence(0.7).build(),
                EvaluationPairResult.builder()
                        .id(4L).taskId(taskId).caseId("case-4")
                        .baselineScore(70.0).challengerScore(78.0)
                        .baselineRiskLevel("MEDIUM").challengerRiskLevel("HIGH")  // 不一致
                        .winner("CHALLENGER").llmJudgeConfidence(0.9).build(),
                EvaluationPairResult.builder()
                        .id(5L).taskId(taskId).caseId("case-5")
                        .baselineScore(85.0).challengerScore(80.0)
                        .baselineRiskLevel("HIGH").challengerRiskLevel("MEDIUM")  // 不一致
                        .winner("BASELINE").llmJudgeConfidence(0.75).build()
        );

        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(pairResultMapper.selectList(any())).thenReturn(results);
        when(winRateCalculator.calculateWinRate(taskId)).thenReturn(winRateStats);

        // when
        EvaluationReport report = evaluationReportService.generateReport(taskId);

        // then
        assertNotNull(report, "报告不应为null");
        assertEquals("NEED_IMPROVEMENT", report.getOverallSuggestion(), "风险一致性<0.6时应返回NEED_IMPROVEMENT");
    }
}