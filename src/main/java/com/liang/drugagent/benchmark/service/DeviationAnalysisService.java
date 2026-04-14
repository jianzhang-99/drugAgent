package com.liang.drugagent.benchmark.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.benchmark.entity.EvaluationDeviationRecord;
import com.liang.drugagent.benchmark.entity.ManualReviewSample;
import com.liang.drugagent.benchmark.mapper.EvaluationDeviationRecordMapper;
import com.liang.drugagent.benchmark.mapper.ManualReviewSampleMapper;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 偏差分析服务。
 *
 * <p>负责计算各规则的 precision/recall/F1，识别系统性偏差模式，
 * 并输出偏差报告用于Prompt迭代优化。
 *
 * <p>该服务不修改任何数据，仅做分析统计。
 */
@Slf4j
@Service
public class DeviationAnalysisService {

    private final ManualReviewSampleMapper sampleMapper;
    private final EvaluationDeviationRecordMapper deviationMapper;
    private final ObjectMapper objectMapper;

    public DeviationAnalysisService(ManualReviewSampleMapper sampleMapper,
                                    EvaluationDeviationRecordMapper deviationMapper,
                                    ObjectMapper objectMapper) {
        this.sampleMapper = sampleMapper;
        this.deviationMapper = deviationMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 分析已审核样本的偏差。
     *
     * @param sampleId 样本ID
     * @param manualResult 人工判定结果
     * @param manualComment 人工审核意见
     * @return 偏差记录列表
     */
    public List<EvaluationDeviationRecord> analyzeDeviation(String sampleId,
                                                              Map<String, Object> manualResult,
                                                              String manualComment) {
        log.info("[偏差分析] 开始分析样本: sampleId={}", sampleId);

        ManualReviewSample sample = sampleMapper.selectOne(
                new LambdaQueryWrapper<ManualReviewSample>()
                        .eq(ManualReviewSample::getSampleId, sampleId));

        if (sample == null) {
            log.error("[偏差分析] 样本不存在: sampleId={}", sampleId);
            return Collections.emptyList();
        }

        // 解析系统判定结果
        List<RuleHit> systemHits = parseSystemHits(sample.getSystemResult());
        Set<String> manualHitRules = extractManualHitRules(manualResult);

        // 计算偏差
        List<EvaluationDeviationRecord> deviations = computeDeviations(
                sample, systemHits, manualHitRules, manualComment);

        // 持久化偏差记录
        for (EvaluationDeviationRecord deviation : deviations) {
            deviationMapper.insert(deviation);
        }

        // 更新样本状态
        sample.setReviewStatus("COMPLETED");
        sample.setManualResult(toJson(manualResult));
        sample.setReviewComment(manualComment);
        sampleMapper.updateById(sample);

        log.info("[偏差分析] 分析完成: sampleId={}, 偏差记录数={}", sampleId, deviations.size());
        return deviations;
    }

    /**
     * 计算各规则的 precision/recall/F1。
     *
     * @return 规则性能指标列表
     */
    public List<RuleMetrics> calculateRuleMetrics() {
        log.info("[偏差分析] 开始计算规则性能指标");

        List<EvaluationDeviationRecord> allDeviations = deviationMapper.selectList(null);

        if (allDeviations.isEmpty()) {
            log.warn("[偏差分析] 无偏差记录，无法计算指标");
            return Collections.emptyList();
        }

        // 按规则编码分组
        Map<String, List<EvaluationDeviationRecord>> byRule = allDeviations.stream()
                .collect(Collectors.groupingBy(EvaluationDeviationRecord::getRuleCode));

        List<RuleMetrics> metricsList = new ArrayList<>();
        for (Map.Entry<String, List<EvaluationDeviationRecord>> entry : byRule.entrySet()) {
            String ruleCode = entry.getKey();
            List<EvaluationDeviationRecord> deviations = entry.getValue();

            // 统计各类偏差数量
            long falsePositive = deviations.stream()
                    .filter(d -> "FALSE_POSITIVE".equals(d.getDeviationType()))
                    .count();
            long falseNegative = deviations.stream()
                    .filter(d -> "FALSE_NEGATIVE".equals(d.getDeviationType()))
                    .count();
            long truePositive = deviations.stream()
                    .filter(d -> !("FALSE_POSITIVE".equals(d.getDeviationType()) ||
                                   "FALSE_NEGATIVE".equals(d.getDeviationType())))
                    .count();

            // 计算 precision/recall/F1
            double precision = truePositive + falsePositive > 0
                    ? (double) truePositive / (truePositive + falsePositive) : 0.0;
            double recall = truePositive + falseNegative > 0
                    ? (double) truePositive / (truePositive + falseNegative) : 0.0;
            double f1 = precision + recall > 0
                    ? 2 * precision * recall / (precision + recall) : 0.0;

            RuleMetrics metrics = RuleMetrics.builder()
                    .ruleCode(ruleCode)
                    .ruleName(deviations.get(0).getRuleName())
                    .totalCases(deviations.size())
                    .truePositive(truePositive)
                    .falsePositive(falsePositive)
                    .falseNegative(falseNegative)
                    .precision(precision)
                    .recall(recall)
                    .f1Score(f1)
                    .build();

            metricsList.add(metrics);
        }

        // 按F1降序排列
        metricsList.sort(Comparator.comparing(RuleMetrics::getF1Score).reversed());

        log.info("[偏差分析] 规则性能指标计算完成: 规则数={}", metricsList.size());
        return metricsList;
    }

    /**
     * 识别系统性偏差模式。
     *
     * <p>分析偏差记录，识别具有共性的问题模式，返回改进建议。
     */
    public List<DeviationPattern> identifySystematicPatterns() {
        log.info("[偏差分析] 开始识别系统性偏差模式");

        List<EvaluationDeviationRecord> deviations = deviationMapper.selectList(null);
        if (deviations.isEmpty()) {
            return Collections.emptyList();
        }

        List<DeviationPattern> patterns = new ArrayList<>();

        // 模式1：高频假阳性规则
        Map<String, Long> fpByRule = deviations.stream()
                .filter(d -> "FALSE_POSITIVE".equals(d.getDeviationType()))
                .collect(Collectors.groupingBy(EvaluationDeviationRecord::getRuleCode, Collectors.counting()));

        fpByRule.entrySet().stream()
                .filter(e -> e.getValue() >= 3) // 至少3次假阳性
                .forEach(e -> {
                    DeviationPattern pattern = DeviationPattern.builder()
                            .patternType("HIGH_FALSE_POSITIVE")
                            .ruleCode(e.getKey())
                            .occurrenceCount(e.getValue().intValue())
                            .description("规则 " + e.getKey() + " 存在高频假阳性，可能需要优化Prompt或调整阈值")
                            .promptImprovement("建议检查该规则的判定条件，增加更多约束条件减少误判")
                            .build();
                    patterns.add(pattern);
                });

        // 模式2：高频假阴性规则
        Map<String, Long> fnByRule = deviations.stream()
                .filter(d -> "FALSE_NEGATIVE".equals(d.getDeviationType()))
                .collect(Collectors.groupingBy(EvaluationDeviationRecord::getRuleCode, Collectors.counting()));

        fnByRule.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .forEach(e -> {
                    DeviationPattern pattern = DeviationPattern.builder()
                            .patternType("HIGH_FALSE_NEGATIVE")
                            .ruleCode(e.getKey())
                            .occurrenceCount(e.getValue().intValue())
                            .description("规则 " + e.getKey() + " 存在高频假阴性，可能遗漏了真实风险")
                            .promptImprovement("建议加强该规则的语义分析能力或降低判定阈值")
                            .build();
                    patterns.add(pattern);
                });

        // 模式3：评分差异模式（系统评分与人工评分差异较大）
        List<EvaluationDeviationRecord> scoreDiffRecords = deviations.stream()
                .filter(d -> "SCORE_DIFF".equals(d.getDeviationType()))
                .toList();

        if (!scoreDiffRecords.isEmpty()) {
            double avgDiff = scoreDiffRecords.stream()
                    .mapToInt(d -> Math.abs(
                            (d.getSystemScore() != null ? d.getSystemScore() : 0) -
                            (d.getManualScore() != null ? d.getManualScore() : 0)))
                    .average()
                    .orElse(0.0);

            DeviationPattern pattern = DeviationPattern.builder()
                    .patternType("SCORE_DEVIATION")
                    .occurrenceCount(scoreDiffRecords.size())
                    .description("存在 " + scoreDiffRecords.size() + " 例评分差异案例，平均差异 " + String.format("%.1f", avgDiff) + " 分")
                    .promptImprovement("建议审视评分权重配置和规则融合逻辑")
                    .build();
            patterns.add(pattern);
        }

        log.info("[偏差分析] 识别到 {} 个系统性偏差模式", patterns.size());
        return patterns;
    }

    /**
     * 生成偏差报告。
     */
    public DeviationReport generateDeviationReport() {
        log.info("[偏差分析] 生成偏差报告");

        List<RuleMetrics> ruleMetrics = calculateRuleMetrics();
        List<DeviationPattern> patterns = identifySystematicPatterns();

        // 计算整体统计
        int totalSamples = sampleMapper.selectCount(null).intValue();
        int completedSamples = sampleMapper.selectCount(
                new LambdaQueryWrapper<ManualReviewSample>()
                        .ne(ManualReviewSample::getReviewStatus, "PENDING")).intValue();
        int totalDeviations = deviationMapper.selectCount(null).intValue();

        DeviationReport report = DeviationReport.builder()
                .totalSamples(totalSamples)
                .completedSamples(completedSamples)
                .totalDeviations(totalDeviations)
                .ruleMetrics(ruleMetrics)
                .deviationPatterns(patterns)
                .generatedAt(new Date())
                .build();

        log.info("[偏差分析] 偏差报告生成完成: 规则数={}, 模式数={}", ruleMetrics.size(), patterns.size());
        return report;
    }

    // ---- internal ----

    private List<RuleHit> parseSystemHits(String systemResult) {
        if (systemResult == null || systemResult.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(systemResult, new TypeReference<List<RuleHit>>() {});
        } catch (JsonProcessingException e) {
            log.warn("[偏差分析] 解析系统结果失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Set<String> extractManualHitRules(Map<String, Object> manualResult) {
        if (manualResult == null) {
            return Collections.emptySet();
        }
        // 假设人工结果中包含 hitRules 字段
        Object hitRules = manualResult.get("hitRules");
        if (hitRules instanceof List) {
            return new HashSet<>((List<String>) hitRules);
        }
        return Collections.emptySet();
    }

    private List<EvaluationDeviationRecord> computeDeviations(ManualReviewSample sample,
                                                                List<RuleHit> systemHits,
                                                                Set<String> manualHitRules,
                                                                String manualComment) {
        List<EvaluationDeviationRecord> deviations = new ArrayList<>();
        Set<String> systemHitRuleCodes = systemHits.stream()
                .map(RuleHit::getRuleCode)
                .collect(Collectors.toSet());

        // 系统命中但人工未命中 -> 假阳性
        for (RuleHit hit : systemHits) {
            if (!manualHitRules.contains(hit.getRuleCode())) {
                EvaluationDeviationRecord deviation = EvaluationDeviationRecord.builder()
                        .sampleId(sample.getSampleId())
                        .caseId(sample.getCaseId())
                        .ruleCode(hit.getRuleCode())
                        .ruleName(hit.getRuleName())
                        .deviationType("FALSE_POSITIVE")
                        .systemConfidence(hit.getConfidence())
                        .systemScore(sample.getRiskScore())
                        .systemJudgment(toJson(hit))
                        .deviationDescription("系统判定命中规则 " + hit.getRuleCode() + "，但人工审核未认可")
                        .promptImprovement(generateImprovementSuggestion("FALSE_POSITIVE", hit))
                        .build();
                deviations.add(deviation);
            }
        }

        // 人工命中但系统未命中 -> 假阴性
        for (String ruleCode : manualHitRules) {
            if (!systemHitRuleCodes.contains(ruleCode)) {
                EvaluationDeviationRecord deviation = EvaluationDeviationRecord.builder()
                        .sampleId(sample.getSampleId())
                        .caseId(sample.getCaseId())
                        .ruleCode(ruleCode)
                        .ruleName(ruleCode)
                        .deviationType("FALSE_NEGATIVE")
                        .manualScore(sample.getRiskScore())
                        .deviationDescription("人工判定命中规则 " + ruleCode + "，但系统未识别")
                        .promptImprovement("建议加强该规则的检测能力，降低漏报率")
                        .build();
                deviations.add(deviation);
            }
        }

        return deviations;
    }

    private String generateImprovementSuggestion(String deviationType, RuleHit hit) {
        if ("FALSE_POSITIVE".equals(deviationType)) {
            return "建议增加" + hit.getRuleName() + "的判定约束条件，减少误判发生";
        }
        return "建议审视相关规则的语义分析逻辑";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("[偏差分析] JSON序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    // ---- inner classes ----

    /**
     * 规则性能指标
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RuleMetrics {
        private String ruleCode;
        private String ruleName;
        private int totalCases;
        private long truePositive;
        private long falsePositive;
        private long falseNegative;
        private double precision;
        private double recall;
        private double f1Score;
    }

    /**
     * 偏差模式
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeviationPattern {
        private String patternType;
        private String ruleCode;
        private int occurrenceCount;
        private String description;
        private String promptImprovement;
    }

    /**
     * 偏差报告
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeviationReport {
        private int totalSamples;
        private int completedSamples;
        private int totalDeviations;
        private List<RuleMetrics> ruleMetrics;
        private List<DeviationPattern> deviationPatterns;
        private Date generatedAt;
    }
}
