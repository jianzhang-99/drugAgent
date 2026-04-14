package com.liang.drugagent.benchmark.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liang.drugagent.benchmark.entity.EvaluationPairResult;
import com.liang.drugagent.benchmark.entity.EvaluationTask;
import com.liang.drugagent.benchmark.mapper.EvaluationPairResultMapper;
import com.liang.drugagent.benchmark.mapper.EvaluationTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 胜率计算服务。
 *
 * <p>负责根据成对评测结果计算胜率、评分差异、风险一致性等指标，
 * 并给出 PASS / NEED_IMPROVEMENT 建议。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WinRateCalculator {

    private final EvaluationPairResultMapper pairResultMapper;
    private final EvaluationTaskMapper taskMapper;

    /**
     * 计算评测任务的胜率统计指标。
     *
     * @param taskId 评测任务ID
     * @return 胜率计算结果
     */
    public WinRateStats calculateWinRate(Long taskId) {
        log.info("[WinRateCalculator] 开始计算胜率统计 - taskId={}", taskId);

        EvaluationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[WinRateCalculator] 评测任务不存在 - taskId={}", taskId);
            return null;
        }

        List<EvaluationPairResult> pairResults = pairResultMapper.selectList(
                new LambdaQueryWrapper<EvaluationPairResult>()
                        .eq(EvaluationPairResult::getTaskId, taskId)
        );

        if (pairResults == null || pairResults.isEmpty()) {
            log.warn("[WinRateCalculator] 评测任务无成对结果 - taskId={}", taskId);
            return null;
        }

        int baselineWins = 0;
        int challengerWins = 0;
        int ties = 0;
        double totalScoreDelta = 0;
        int riskConsistentCount = 0;

        for (EvaluationPairResult result : pairResults) {
            // 统计胜负
            if ("BASELINE".equals(result.getWinner())) {
                baselineWins++;
            } else if ("CHALLENGER".equals(result.getWinner())) {
                challengerWins++;
            } else if ("TIE".equals(result.getWinner())) {
                ties++;
            }

            // 累加评分差异
            if (result.getBaselineScore() != null && result.getChallengerScore() != null) {
                totalScoreDelta += (result.getChallengerScore() - result.getBaselineScore());
            }

            // 统计风险等级一致性
            if (result.getBaselineRiskLevel() != null
                    && result.getChallengerRiskLevel() != null
                    && result.getBaselineRiskLevel().equals(result.getChallengerRiskLevel())) {
                riskConsistentCount++;
            }
        }

        int totalPairs = pairResults.size();
        double avgScoreDelta = totalPairs > 0 ? totalScoreDelta / totalPairs : 0;
        double riskConsistency = totalPairs > 0 ? (double) riskConsistentCount / totalPairs : 0;

        // 计算challenger胜率（排除平局）
        int totalDecisive = challengerWins + baselineWins;
        double challengerWinRate = totalDecisive > 0 ? (double) challengerWins / totalDecisive : 0;

        // 判定建议：challenger胜率 >= 0.5 且 avgScoreDelta >= 0 则为 PASS
        String suggestion = (challengerWinRate >= 0.5 && avgScoreDelta >= 0) ? "PASS" : "NEED_IMPROVEMENT";

        WinRateStats stats = WinRateStats.builder()
                .taskId(taskId)
                .baselineModel(task.getBaselineModel())
                .challengerModel(task.getChallengerModel())
                .totalPairs(totalPairs)
                .baselineWins(baselineWins)
                .challengerWins(challengerWins)
                .ties(ties)
                .challengerWinRate(challengerWinRate)
                .avgScoreDelta(avgScoreDelta)
                .riskConsistency(riskConsistency)
                .suggestion(suggestion)
                .build();

        log.info("[WinRateCalculator] 胜率计算完成 - taskId={}, challengerWinRate={}, avgScoreDelta={}, suggestion={}",
                taskId, challengerWinRate, avgScoreDelta, suggestion);

        return stats;
    }

    /**
     * 胜率统计结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WinRateStats {
        /** 评测任务ID */
        private Long taskId;

        /** Baseline模型名称 */
        private String baselineModel;

        /** Challenger模型名称 */
        private String challengerModel;

        /** 总成对评测数 */
        private int totalPairs;

        /** Baseline获胜次数 */
        private int baselineWins;

        /** Challenger获胜次数 */
        private int challengerWins;

        /** 平局次数 */
        private int ties;

        /** Challenger胜率（不含平局） */
        private double challengerWinRate;

        /** 平均评分差异（challenger - baseline） */
        private double avgScoreDelta;

        /** 风险等级一致性比例 */
        private double riskConsistency;

        /** 建议：PASS / NEED_IMPROVEMENT */
        private String suggestion;
    }
}
