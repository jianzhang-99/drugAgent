package com.liang.drugagent.benchmark.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liang.drugagent.benchmark.entity.EvaluationPairResult;
import com.liang.drugagent.benchmark.entity.EvaluationTask;
import com.liang.drugagent.benchmark.mapper.EvaluationPairResultMapper;
import com.liang.drugagent.benchmark.mapper.EvaluationTaskMapper;
import com.liang.drugagent.benchmark.service.WinRateCalculator.WinRateStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评估报告生成服务。
 *
 * <p>负责汇总LLM评判指标和人工审核指标，生成结构化的评估报告，
 * 包括胜率分析、评分对比、风险一致性等核心维度。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationReportService {

    private final EvaluationTaskMapper taskMapper;
    private final EvaluationPairResultMapper pairResultMapper;
    private final WinRateCalculator winRateCalculator;

    /**
     * 生成评测任务的完整评估报告。
     *
     * @param taskId 评测任务ID
     * @return 结构化评估报告
     */
    public EvaluationReport generateReport(Long taskId) {
        log.info("[EvaluationReportService] 开始生成评估报告 - taskId={}", taskId);

        EvaluationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[EvaluationReportService] 评测任务不存在 - taskId={}", taskId);
            return null;
        }

        List<EvaluationPairResult> pairResults = pairResultMapper.selectList(
                new LambdaQueryWrapper<EvaluationPairResult>()
                        .eq(EvaluationPairResult::getTaskId, taskId)
        );

        // 计算胜率统计
        WinRateStats winRateStats = winRateCalculator.calculateWinRate(taskId);

        // 汇总LLM评判指标
        LlmJudgeMetrics llmJudgeMetrics = calculateLlmJudgeMetrics(pairResults);

        // 汇总评分对比指标
        ScoreComparisonMetrics scoreMetrics = calculateScoreComparisonMetrics(pairResults);

        // 汇总风险一致性指标
        RiskConsistencyMetrics riskMetrics = calculateRiskConsistencyMetrics(pairResults);

        // 构建报告
        EvaluationReport report = EvaluationReport.builder()
                .taskId(taskId)
                .taskName(task.getName())
                .baselineModel(task.getBaselineModel())
                .challengerModel(task.getChallengerModel())
                .sceneType(task.getSceneType())
                .totalCases(task.getTotalCases())
                .completedCases(pairResults != null ? pairResults.size() : 0)
                .status(task.getStatus())
                .llmJudgeMetrics(llmJudgeMetrics)
                .scoreComparisonMetrics(scoreMetrics)
                .riskConsistencyMetrics(riskMetrics)
                .winRateStats(winRateStats)
                .overallSuggestion(determineOverallSuggestion(winRateStats, riskMetrics))
                .build();

        log.info("[EvaluationReportService] 评估报告生成完成 - taskId={}, suggestion={}",
                taskId, report.getOverallSuggestion());

        return report;
    }

    /**
     * 计算LLM评判指标。
     */
    private LlmJudgeMetrics calculateLlmJudgeMetrics(List<EvaluationPairResult> results) {
        if (results == null || results.isEmpty()) {
            return LlmJudgeMetrics.builder().build();
        }

        int totalConfident = 0;
        double totalConfidence = 0;
        int llmJudgedCount = 0;

        for (EvaluationPairResult result : results) {
            if (result.getLlmJudgeConfidence() != null) {
                llmJudgedCount++;
                totalConfidence += result.getLlmJudgeConfidence();
                if (result.getLlmJudgeConfidence() >= 0.7) {
                    totalConfident++;
                }
            }
        }

        double avgConfidence = llmJudgedCount > 0 ? totalConfidence / llmJudgedCount : 0;
        double confidentRate = llmJudgedCount > 0 ? (double) totalConfident / llmJudgedCount : 0;

        return LlmJudgeMetrics.builder()
                .totalPairs(results.size())
                .llmJudgedPairs(llmJudgedCount)
                .avgConfidence(avgConfidence)
                .highConfidenceRate(confidentRate)
                .build();
    }

    /**
     * 计算评分对比指标。
     */
    private ScoreComparisonMetrics calculateScoreComparisonMetrics(List<EvaluationPairResult> results) {
        if (results == null || results.isEmpty()) {
            return ScoreComparisonMetrics.builder().build();
        }

        double totalBaselineScore = 0;
        double totalChallengerScore = 0;
        int baselineHigherCount = 0;
        int challengerHigherCount = 0;
        int equalCount = 0;

        for (EvaluationPairResult result : results) {
            if (result.getBaselineScore() != null && result.getChallengerScore() != null) {
                totalBaselineScore += result.getBaselineScore();
                totalChallengerScore += result.getChallengerScore();

                if (result.getBaselineScore() > result.getChallengerScore()) {
                    baselineHigherCount++;
                } else if (result.getChallengerScore() > result.getBaselineScore()) {
                    challengerHigherCount++;
                } else {
                    equalCount++;
                }
            }
        }

        int validPairs = results.size();
        double avgBaselineScore = validPairs > 0 ? totalBaselineScore / validPairs : 0;
        double avgChallengerScore = validPairs > 0 ? totalChallengerScore / validPairs : 0;

        return ScoreComparisonMetrics.builder()
                .avgBaselineScore(avgBaselineScore)
                .avgChallengerScore(avgChallengerScore)
                .baselineHigherCount(baselineHigherCount)
                .challengerHigherCount(challengerHigherCount)
                .equalCount(equalCount)
                .build();
    }

    /**
     * 计算风险一致性指标。
     */
    private RiskConsistencyMetrics calculateRiskConsistencyMetrics(List<EvaluationPairResult> results) {
        if (results == null || results.isEmpty()) {
            return RiskConsistencyMetrics.builder().build();
        }

        int consistentCount = 0;
        Map<String, Integer> riskLevelDistribution = new HashMap<>();

        for (EvaluationPairResult result : results) {
            if (result.getBaselineRiskLevel() != null && result.getChallengerRiskLevel() != null) {
                if (result.getBaselineRiskLevel().equals(result.getChallengerRiskLevel())) {
                    consistentCount++;
                }
                riskLevelDistribution.merge(result.getBaselineRiskLevel(), 1, Integer::sum);
                riskLevelDistribution.merge(result.getChallengerRiskLevel(), 1, Integer::sum);
            }
        }

        double consistencyRate = (double) consistentCount / results.size();

        return RiskConsistencyMetrics.builder()
                .consistentPairs(consistentCount)
                .totalPairs(results.size())
                .consistencyRate(consistencyRate)
                .riskLevelDistribution(riskLevelDistribution)
                .build();
    }

    /**
     * 综合判定最终建议。
     */
    private String determineOverallSuggestion(WinRateStats winRateStats, RiskConsistencyMetrics riskMetrics) {
        if (winRateStats == null) {
            return "INSUFFICIENT_DATA";
        }

        // 胜率达标且风险一致性好
        if ("PASS".equals(winRateStats.getSuggestion())
                && riskMetrics != null
                && riskMetrics.getConsistencyRate() >= 0.6) {
            return "PASS";
        }

        // 胜率不达标或风险一致性差
        return "NEED_IMPROVEMENT";
    }

    /**
     * 评估报告
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EvaluationReport {
        /** 评测任务ID */
        private Long taskId;

        /** 任务名称 */
        private String taskName;

        /** Baseline模型 */
        private String baselineModel;

        /** Challenger模型 */
        private String challengerModel;

        /** 场景类型 */
        private String sceneType;

        /** 总case数 */
        private Integer totalCases;

        /** 完成case数 */
        private Integer completedCases;

        /** 任务状态 */
        private String status;

        /** LLM评判指标 */
        private LlmJudgeMetrics llmJudgeMetrics;

        /** 评分对比指标 */
        private ScoreComparisonMetrics scoreComparisonMetrics;

        /** 风险一致性指标 */
        private RiskConsistencyMetrics riskConsistencyMetrics;

        /** 胜率统计 */
        private WinRateStats winRateStats;

        /** 总体建议 */
        private String overallSuggestion;
    }

    /**
     * LLM评判指标
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LlmJudgeMetrics {
        /** 总成对数 */
        private int totalPairs;

        /** LLM参与评判的成对数 */
        private int llmJudgedPairs;

        /** 平均置信度 */
        private double avgConfidence;

        /** 高置信度（>=0.7）比例 */
        private double highConfidenceRate;
    }

    /**
     * 评分对比指标
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ScoreComparisonMetrics {
        /** Baseline平均评分 */
        private double avgBaselineScore;

        /** Challenger平均评分 */
        private double avgChallengerScore;

        /** Baseline评分更高的次数 */
        private int baselineHigherCount;

        /** Challenger评分更高的次数 */
        private int challengerHigherCount;

        /** 评分相等次数 */
        private int equalCount;
    }

    /**
     * 风险一致性指标
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RiskConsistencyMetrics {
        /** 风险等级一致的成对数 */
        private int consistentPairs;

        /** 总成对数 */
        private int totalPairs;

        /** 一致性比例 */
        private double consistencyRate;

        /** 风险等级分布 */
        private Map<String, Integer> riskLevelDistribution;
    }
}
