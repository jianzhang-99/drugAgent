package com.liang.drugagent.benchmark.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liang.drugagent.benchmark.entity.EvaluationPairResult;
import com.liang.drugagent.benchmark.entity.EvaluationTask;
import com.liang.drugagent.benchmark.mapper.EvaluationPairResultMapper;
import com.liang.drugagent.benchmark.mapper.EvaluationTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WinRateCalculator 单元测试。
 *
 * <p>测试胜率计算服务的各项功能，包括正常计算、边界情况等。</p>
 */
@ExtendWith(MockitoExtension.class)
class WinRateCalculatorTest {

    @Mock
    private EvaluationPairResultMapper pairResultMapper;

    @Mock
    private EvaluationTaskMapper taskMapper;

    private WinRateCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new WinRateCalculator(pairResultMapper, taskMapper);
    }

    /**
     * Case 1: 正常胜率计算 - challenger胜3, baseline胜5, tie2 -> 期望 0.375
     * challengerWins=3, baselineWins=5, ties=2
     * challengerWinRate = 3 / (3 + 5) = 3/8 = 0.375
     */
    @Test
    @DisplayName("Case 1: 正常胜率计算 - challenger胜3, baseline胜5, tie2 -> 期望 0.375")
    void testNormalWinRateCalculation() {
        // 准备评测任务
        EvaluationTask task = EvaluationTask.builder()
                .id(1L)
                .baselineModel("baseline-v1")
                .challengerModel("challenger-v1")
                .build();
        when(taskMapper.selectById(1L)).thenReturn(task);

        // 准备成对结果: challenger胜3, baseline胜5, tie2
        // 总10对，challengerWinRate = 3/(3+5) = 0.375
        List<EvaluationPairResult> results = Arrays.asList(
                createPairResult(1L, "BASELINE", 80.0, 75.0, "LOW", "LOW"),
                createPairResult(1L, "BASELINE", 85.0, 80.0, "MEDIUM", "MEDIUM"),
                createPairResult(1L, "BASELINE", 90.0, 85.0, "HIGH", "HIGH"),
                createPairResult(1L, "BASELINE", 78.0, 70.0, "MEDIUM", "MEDIUM"),
                createPairResult(1L, "BASELINE", 88.0, 82.0, "HIGH", "MEDIUM"),
                createPairResult(1L, "CHALLENGER", 75.0, 80.0, "LOW", "LOW"),
                createPairResult(1L, "CHALLENGER", 78.0, 85.0, "MEDIUM", "LOW"),
                createPairResult(1L, "CHALLENGER", 70.0, 90.0, "HIGH", "LOW"),
                createPairResult(1L, "TIE", 85.0, 85.0, "LOW", "LOW"),
                createPairResult(1L, "TIE", 90.0, 90.0, "MEDIUM", "MEDIUM")
        );
        when(pairResultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(results);

        // 执行计算
        WinRateCalculator.WinRateStats stats = calculator.calculateWinRate(1L);

        // 验证
        assertNotNull(stats);
        assertEquals(10, stats.getTotalPairs());
        assertEquals(5, stats.getBaselineWins());
        assertEquals(3, stats.getChallengerWins());
        assertEquals(2, stats.getTies());
        // challengerWinRate = 3/(3+5) = 0.375
        assertEquals(0.375, stats.getChallengerWinRate(), 0.001);
        // avgScoreDelta = (challenger - baseline)之和 / 10
        // = (-5-5-5-8-6+5+7+20+0+0) / 10 = 3 / 10 = 0.3
        assertEquals(0.3, stats.getAvgScoreDelta(), 0.001);
    }

    /**
     * Case 2: 全胜情况 - challenger胜5, baseline胜0 -> 期望 1.0, PASS
     */
    @Test
    @DisplayName("Case 2: 全胜情况 - challenger胜5, baseline胜0 -> 期望 1.0, PASS")
    void testAllWinsForChallenger() {
        EvaluationTask task = EvaluationTask.builder()
                .id(2L)
                .baselineModel("baseline-v1")
                .challengerModel("challenger-v1")
                .build();
        when(taskMapper.selectById(2L)).thenReturn(task);

        List<EvaluationPairResult> results = Arrays.asList(
                createPairResult(2L, "CHALLENGER", 70.0, 80.0, "HIGH", "LOW"),
                createPairResult(2L, "CHALLENGER", 75.0, 85.0, "MEDIUM", "LOW"),
                createPairResult(2L, "CHALLENGER", 80.0, 90.0, "HIGH", "MEDIUM"),
                createPairResult(2L, "CHALLENGER", 78.0, 88.0, "MEDIUM", "MEDIUM"),
                createPairResult(2L, "CHALLENGER", 72.0, 82.0, "LOW", "LOW")
        );
        when(pairResultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(results);

        WinRateCalculator.WinRateStats stats = calculator.calculateWinRate(2L);

        assertNotNull(stats);
        assertEquals(5, stats.getTotalPairs());
        assertEquals(0, stats.getBaselineWins());
        assertEquals(5, stats.getChallengerWins());
        assertEquals(0, stats.getTies());
        assertEquals(1.0, stats.getChallengerWinRate(), 0.001);
        // avgScoreDelta = (10+10+10+10+10) / 5 = 10.0
        assertEquals(10.0, stats.getAvgScoreDelta(), 0.001);
        assertEquals("PASS", stats.getSuggestion());
    }

    /**
     * Case 3: 评分差异计算 - baseline[80,85,90], challenger[85,82,88] -> 期望 avgScoreDelta = 0
     * delta = (85-80)+(82-85)+(88-90) = 5-3-2 = 0, avg = 0/3 = 0
     */
    @Test
    @DisplayName("Case 3: 评分差异计算 - baseline[80,85,90], challenger[85,82,88] -> 期望 avgScoreDelta = 0")
    void testScoreDeltaCalculation() {
        EvaluationTask task = EvaluationTask.builder()
                .id(3L)
                .baselineModel("baseline-v1")
                .challengerModel("challenger-v1")
                .build();
        when(taskMapper.selectById(3L)).thenReturn(task);

        List<EvaluationPairResult> results = Arrays.asList(
                createPairResult(3L, "CHALLENGER", 80.0, 85.0, "LOW", "LOW"),
                createPairResult(3L, "BASELINE", 85.0, 82.0, "MEDIUM", "MEDIUM"),
                createPairResult(3L, "TIE", 90.0, 88.0, "HIGH", "HIGH")
        );
        when(pairResultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(results);

        WinRateCalculator.WinRateStats stats = calculator.calculateWinRate(3L);

        assertNotNull(stats);
        assertEquals(0, stats.getAvgScoreDelta(), 0.001);
    }

    /**
     * Case 4: 风险一致性 - 4对中3对相同 -> 期望 0.75
     */
    @Test
    @DisplayName("Case 4: 风险一致性 - 4对中3对相同 -> 期望 0.75")
    void testRiskConsistency() {
        EvaluationTask task = EvaluationTask.builder()
                .id(4L)
                .baselineModel("baseline-v1")
                .challengerModel("challenger-v1")
                .build();
        when(taskMapper.selectById(4L)).thenReturn(task);

        // 4对中3对风险等级相同 (第1、2、4对相同)
        List<EvaluationPairResult> results = Arrays.asList(
                createPairResult(4L, "CHALLENGER", 80.0, 85.0, "LOW", "LOW"),      // 相同
                createPairResult(4L, "BASELINE", 85.0, 80.0, "MEDIUM", "MEDIUM"),  // 相同
                createPairResult(4L, "TIE", 90.0, 88.0, "HIGH", "LOW"),             // 不同
                createPairResult(4L, "CHALLENGER", 75.0, 78.0, "LOW", "LOW")       // 相同
        );
        when(pairResultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(results);

        WinRateCalculator.WinRateStats stats = calculator.calculateWinRate(4L);

        assertNotNull(stats);
        assertEquals(0.75, stats.getRiskConsistency(), 0.001);
    }

    /**
     * Case 5: 建议判定 - 胜率>=0.5 且 avgScoreDelta>=0 -> PASS
     */
    @Test
    @DisplayName("Case 5: 建议判定 - 胜率>=0.5 且 avgScoreDelta>=0 -> PASS")
    void testSuggestionPass() {
        EvaluationTask task = EvaluationTask.builder()
                .id(5L)
                .baselineModel("baseline-v1")
                .challengerModel("challenger-v1")
                .build();
        when(taskMapper.selectById(5L)).thenReturn(task);

        // challenger胜3, baseline胜1: winRate = 3/(3+1) = 0.75 >= 0.5
        // avgScoreDelta = (5+5+5-3) / 4 = 12/4 = 3.0 >= 0
        List<EvaluationPairResult> results = Arrays.asList(
                createPairResult(5L, "CHALLENGER", 75.0, 80.0, "LOW", "LOW"),
                createPairResult(5L, "CHALLENGER", 80.0, 85.0, "MEDIUM", "MEDIUM"),
                createPairResult(5L, "CHALLENGER", 85.0, 90.0, "HIGH", "HIGH"),
                createPairResult(5L, "BASELINE", 90.0, 87.0, "HIGH", "MEDIUM")
        );
        when(pairResultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(results);

        WinRateCalculator.WinRateStats stats = calculator.calculateWinRate(5L);

        assertNotNull(stats);
        assertEquals(0.75, stats.getChallengerWinRate(), 0.001);
        assertEquals(3.0, stats.getAvgScoreDelta(), 0.001);
        assertEquals("PASS", stats.getSuggestion());
    }

    /**
     * Case 6: 边界情况 - 空结果列表
     */
    @Test
    @DisplayName("Case 6: 边界情况 - 空结果列表")
    void testEmptyResults() {
        EvaluationTask task = EvaluationTask.builder()
                .id(6L)
                .baselineModel("baseline-v1")
                .challengerModel("challenger-v1")
                .build();
        when(taskMapper.selectById(6L)).thenReturn(task);
        when(pairResultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        WinRateCalculator.WinRateStats stats = calculator.calculateWinRate(6L);

        assertNull(stats);
        verify(pairResultMapper).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * Case 7: 边界情况 - 任务不存在
     */
    @Test
    @DisplayName("Case 7: 边界情况 - 任务不存在")
    void testTaskNotFound() {
        when(taskMapper.selectById(999L)).thenReturn(null);

        WinRateCalculator.WinRateStats stats = calculator.calculateWinRate(999L);

        assertNull(stats);
        verify(taskMapper).selectById(999L);
        verifyNoInteractions(pairResultMapper);
    }

    /**
     * 辅助方法：创建EvaluationPairResult
     */
    private EvaluationPairResult createPairResult(Long taskId, String winner, Double baselineScore,
                                                   Double challengerScore, String baselineRisk,
                                                   String challengerRisk) {
        return EvaluationPairResult.builder()
                .taskId(taskId)
                .caseId("case-" + System.nanoTime())
                .winner(winner)
                .baselineScore(baselineScore)
                .challengerScore(challengerScore)
                .baselineRiskLevel(baselineRisk)
                .challengerRiskLevel(challengerRisk)
                .build();
    }
}