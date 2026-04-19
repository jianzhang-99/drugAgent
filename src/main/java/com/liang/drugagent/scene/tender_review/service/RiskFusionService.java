package com.liang.drugagent.scene.tender_review.service;

import com.liang.drugagent.scene.tender_review.model.ExemptionHit;
import com.liang.drugagent.scene.tender_review.model.RiskFusionResult;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 风险融合服务。
 * 负责依据各项规则的命中权重、命中丰富度、多源证据关联性进行加权计算。
 * 该服务将离散的规则命中碎片融合成单一的、可解释的综合风险评分及分级。
 *
 * <p>优化后的融合算法特点：
 * <ul>
 *   <li>引入加权 Sigmoid 融合替代离散阈值，实现连续平滑评分</li>
 *   <li>区分 LLM 语义命中与确定性规则的置信度映射</li>
 *   <li>关联规则（W-M2 + W-M3）同时命中时触发超线性加分</li>
 *   <li>同类型重复命中引入边际收益递减机制</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Service
public class RiskFusionService {

    /**
     * Sigmoid 曲线陡峭度参数。
     * 控制从低置信度到高置信度的过渡速度。
     */
    private static final double SIGMOID_STEEPNESS = 15.0;

    /**
     * Sigmoid 曲线中点偏移。
     * 调整置信度映射的基准位置。
     */
    private static final double SIGMOID_MIDPOINT = 0.75;

    /**
     * LLM 语义命中权重上限（确定性规则的基准分）。
     */
    private static final int LLM_BASE_WEIGHT_CAP = 70;

    /**
     * 关联规则超线性加成系数。
     * 当 W-M2 + W-M3 同时命中时，乘以此系数作为额外加成。
     */
    private static final double SYNERGY_MULTIPLIER = 1.5;

    /**
     * 重复命中衰减基准率。
     * 每多一次同类型命中，前一次命中的边际收益按此比例衰减。
     */
    private static final double REPEAT_DECAY_RATE = 0.75;

    /**
     * W-M4 鍣煶琛板噺绯绘暟銆?
     * 褰?W-M4 涓庡涓叿浣撹鍒欐垨璇箟瑙勫垯鍚屾椂鍑虹幇鏃讹紝搴斿皢鍏跺綋浣滆儗鏅櫔闊炽€?
     */
    private static final double W_M4_NOISE_DECAY_FACTOR = 0.3;

    /**
     * W-P4 鍣煶琛板噺绯绘暟銆?
     * 褰?W-P4 涓庡叾浠栨洿鍏蜂綋鐨勬枃鏈垨瑙勫垯鍏卞瓨鏃讹紝搴旈檷浣庡叾鎵€鍗犳潈閲嶃€?
     */
    private static final double W_P4_NOISE_DECAY_FACTOR = 0.4;

    /**
     * W-M4 澶氬懡涓椂瑙嗕负鍣煶鐨勫叾浠栧疄璐ㄨ鍒欍€?
     */
    private static final Set<String> W_M4_NOISE_COMPETITORS = Set.of(
            "W-M1", "W-M2", "W-M3", "W-M6", "W-M8", "W-P1", "W-P2", "W-P3", "W-P4", "W-P6"
    );

    /**
     * W-P4 澶氬懡涓椂瑙嗕负鍣煶鐨勫叾浠栧疄璐ㄨ鍒欍€?
     */
    private static final Set<String> W_P4_NOISE_COMPETITORS = Set.of(
            "W-M1", "W-M2", "W-M3", "W-M4", "W-M6", "W-M8", "W-P1", "W-P2", "W-P3", "W-P6"
    );

    /**
     * 融合多项风险证据。
     *
     * @param data 原始输入数据（用于获取文档上下文）
     * @param effectiveHits 最终生效（未被彻底免责）的风险命中项
     * @param exemptionHits 已发生的免责过程（用于负向反馈调权重）
     * @return 包含融合评分、风险等级、摘要理由的结果对象
     */
    public RiskFusionResult fuse(TenderReviewData data, List<RuleHit> effectiveHits, List<ExemptionHit> exemptionHits) {
        RiskFusionResult result = new RiskFusionResult();

        List<RuleHit> hits = effectiveHits == null ? List.of() : effectiveHits;
        List<ExemptionHit> exemptions = exemptionHits == null ? List.of() : exemptionHits;

        // 0. W-M4 噪音处理：如果 W-M4 与其他实质风险共存，进行权重衰减
        // 原因：所有投标文件都使用标准模板，W-M4 容易触发但不代表真正的围标风险
        applyW_M4NoiseReduction(hits);
        applyW_P4NoiseReduction(hits);

        // 1. 处理无命中项或全部免责的情况
        if (hits.isEmpty()) {
            result.setRiskLevel("LOW");
            result.setScore(exemptions.isEmpty() ? 0 : 20);
            result.setSummary(exemptions.isEmpty()
                    ? "规则扫描后未保留高风险命中，当前结果显示该批标书在规则维度偏差较小。"
                    : "检测到原始命中，但均已被豁免规则降权或过滤。");
            if (!exemptions.isEmpty()) {
                result.setReasonCodes(List.of("EXEMPTION_DOWNGRADE"));
            }
            return result;
        }

        // 2. 计算各类命中的 Sigmoid 加权分
        double baseScore = calculateSigmoidWeightedScore(hits);

        // 3. 关联规则超线性加成（W-M2 + W-M3 同时命中）
        double synergyBonus = calculateSynergyBonus(hits);

        // 4. 多维风险累加计算（改进版）
        double score = baseScore;
        List<String> reasonCodes = new ArrayList<>();

        // 规则覆盖度加分（连续加权版）
        Set<String> ruleCodes = new LinkedHashSet<>();
        for (RuleHit hit : hits) {
            if (hit.getRuleCode() != null) {
                ruleCodes.add(hit.getRuleCode());
            }
        }
        if (ruleCodes.size() >= 2) {
            score += 7 * Math.min(ruleCodes.size() - 1, 3) * 0.5;
            reasonCodes.add("MULTI_RULE_CO_OCCURRENCE");
        }

        // 命中次数累积加分（带衰减机制）
        double hitAccumulationBonus = calculateDecayedHitBonus(hits);
        if (hitAccumulationBonus > 0) {
            score += hitAccumulationBonus;
            reasonCodes.add("MULTI_HIT_ACCUMULATION");
        }

        // 跨文档证据加分（多文档场景交叉验证）
        Set<String> documentIds = new LinkedHashSet<>();
        int evidenceCount = 0;
        boolean hasHighPriorityHardRule = false;
        for (RuleHit hit : hits) {
            if (hit.getDocumentIds() != null) {
                documentIds.addAll(hit.getDocumentIds());
            }
            if (hit.getEvidences() != null) {
                evidenceCount += hit.getEvidences().size();
            }
            // 硬规则判定逻辑：高优先级且权重 >= 85
            if ("HIGH".equals(hit.getPriority()) && effectiveWeight(hit) != null && effectiveWeight(hit) >= 85) {
                hasHighPriorityHardRule = true;
            }
        }

        // 跨文档交叉验证加分（≥3份文档时加强）
        if (documentIds.size() >= 2) {
            double crossDocBonus = 5;
            if (documentIds.size() >= 3) {
                crossDocBonus = 8; // 多文档交叉验证逻辑
                reasonCodes.add("CROSS_DOCUMENT_VALIDATION");
            } else {
                reasonCodes.add("CROSS_DOCUMENT_EVIDENCE");
            }
            score += crossDocBonus;
        }

        // 证据丰富度加分（连续加权版）
        if (evidenceCount >= 4) {
            score += 3 + Math.min(evidenceCount - 4, 6) * 0.5;
            reasonCodes.add("EVIDENCE_SUFFICIENT");
        }

        // 高优规则加成
        if (hasHighPriorityHardRule) {
            score += 8;
            reasonCodes.add("HIGH_PRIORITY_RULE");
        }

        // 关联规则超线性加成
        if (synergyBonus > 0) {
            score += synergyBonus;
            reasonCodes.add("SYNERGY_BONUS");
        }

        // 5. 免责项负向削减权重（改进版）
        if (!exemptions.isEmpty()) {
            score -= Math.min(exemptions.size() * 3, 15);
            reasonCodes.add("EXEMPTION_DOWNGRADE");
        }

        // 6. 归一化评分 (0-100)
        int finalScore = (int) Math.max(0, Math.min(score, 100));
        result.setScore(finalScore);
        result.setRiskLevel(resolveRiskLevel(finalScore, hasHighPriorityHardRule, synergyBonus > 0));
        result.setReasonCodes(reasonCodes);
        result.setSummary(buildSummary(finalScore, hits, exemptions.size(), documentIds.size()));
        return result;
    }

    /**
     * 计算 Sigmoid 加权分数。
     * 替代离散阶梯式权重，对 LLM 置信度进行连续平滑映射。
     *
     * <p>公式：weight = baseWeight * sigmoid(steepness * (confidence - midpoint))
     * 其中 sigmoid(x) = 1 / (1 + exp(-x))
     */
    private double calculateSigmoidWeightedScore(List<RuleHit> hits) {
        double totalWeightedScore = 0.0;

        for (RuleHit hit : hits) {
            Integer weight = effectiveWeight(hit);
            if (weight == null || weight <= 0) {
                continue;
            }

            Double confidence = hit.getConfidence();
            if (confidence != null) {
                // LLM 语义命中：使用 Sigmoid 连续加权
                double normalizedConfidence = Math.max(0.0, Math.min(1.0, confidence));
                double sigmoidValue = 1.0 / (1.0 + Math.exp(-SIGMOID_STEEPNESS * (normalizedConfidence - SIGMOID_MIDPOINT)));
                // 将 Sigmoid 输出映射到 [0.5, 1.0] 范围
                double confidenceMultiplier = 0.5 + 0.5 * sigmoidValue;
                totalWeightedScore += weight * confidenceMultiplier;
            } else {
                // 确定性规则：直接使用权重
                totalWeightedScore += weight;
            }
        }

        return totalWeightedScore;
    }

    /**
     * 计算关联规则超线性加成。
     * W-M2（联系方式近邻）与 W-M3（团队重叠）同时命中时，
     * 说明投标方之间存在多重关联特征，风险呈指数级上升。
     */
    private double calculateSynergyBonus(List<RuleHit> hits) {
        Set<String> hitRuleCodes = new LinkedHashSet<>();
        for (RuleHit hit : hits) {
            if (hit.getRuleCode() != null) {
                hitRuleCodes.add(hit.getRuleCode());
            }
        }

        // W-M2 和 W-M3 同时命中
        if (hitRuleCodes.contains("W-M2") && hitRuleCodes.contains("W-M3")) {
            // 找到 W-M2 和 W-M3 的最高权重
            int wm2MaxWeight = 0;
            int wm3MaxWeight = 0;
            for (RuleHit hit : hits) {
                Integer weight = effectiveWeight(hit);
                if (weight == null) continue;
                if ("W-M2".equals(hit.getRuleCode())) {
                    wm2MaxWeight = Math.max(wm2MaxWeight, weight);
                } else if ("W-M3".equals(hit.getRuleCode())) {
                    wm3MaxWeight = Math.max(wm3MaxWeight, weight);
                }
            }
            // 超线性加成：取两者最大值的几何平均，乘以协同系数
            double geometricMean = Math.sqrt(wm2MaxWeight * wm3MaxWeight);
            return geometricMean * (SYNERGY_MULTIPLIER - 1);
        }

        return 0;
    }

    /**
     * 计算带衰减的命中累加加分。
     * 同类型重复命中的边际收益递减，避免刷分效应。
     */
    private double calculateDecayedHitBonus(List<RuleHit> hits) {
        if (hits.size() < 3) {
            return 0;
        }

        // 按规则编码分组，统计每组命中次数
        Map<String, Integer> ruleCodeCount = new LinkedHashMap<>();
        for (RuleHit hit : hits) {
            String ruleCode = hit.getRuleCode();
            if (ruleCode != null) {
                ruleCodeCount.merge(ruleCode, 1, Integer::sum);
            }
        }

        double totalBonus = 0;
        int totalExtraHits = hits.size() - 2; // 超过2次的命中才开始计算衰减

        if (totalExtraHits > 0) {
            // 基准加成（无衰减）
            double baseBonus = 5;
            // 衰减后的实际加成
            double decayedBonus = baseBonus * Math.pow(REPEAT_DECAY_RATE, Math.log(totalExtraHits + 1) / Math.log(2));
            totalBonus = decayedBonus;
        }

        return totalBonus;
    }

    /**
     * 根据评分和规则命中情况判定风险等级。
     *
     * @param score 综合风险分
     * @param hasHighPriorityHardRule 是否命中高优硬规则
     * @param hasSynergyBonus 是否有关联规则超线性加成
     * @return 风险等级 (HIGH, MEDIUM, LOW)
     */
    private String resolveRiskLevel(int score, boolean hasHighPriorityHardRule, boolean hasSynergyBonus) {
        // 有关联规则超线性加成时，降低 HIGH 门槛
        if (hasSynergyBonus && score >= 75) {
            return "HIGH";
        }
        if (hasHighPriorityHardRule && score >= 85) {
            return "HIGH";
        }
        if (score >= 85) {
            return "HIGH";
        }
        if (score >= 60) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /**
     * 构建风险简报，包含具体命中的规则名称。
     */
    private String buildSummary(int score, List<RuleHit> hits, int exemptionCount, int documentCount) {
        // 提取有效命中的规则名称
        Set<String> ruleNames = new LinkedHashSet<>();
        Set<String> ruleCodes = new LinkedHashSet<>();
        for (RuleHit hit : hits) {
            if (hit.getRuleName() != null && !hit.getRuleName().isBlank()) {
                ruleNames.add(hit.getRuleName());
            }
            if (hit.getRuleCode() != null) {
                ruleCodes.add(hit.getRuleCode());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("风险融合分值=").append(score);
        sb.append("，有效命中=").append(hits.size()).append("项");
        if (!ruleNames.isEmpty()) {
            sb.append("（").append(String.join("、", ruleNames)).append("）");
        }
        sb.append("，规则类型数=").append(ruleCodes.size());
        sb.append("，文档数=").append(documentCount);
        if (exemptionCount > 0) {
            sb.append("，豁免项=").append(exemptionCount);
        }
        return sb.toString();
    }

    /**
     * 获取 RuleHit 的生效权重。
     * 优先取调整后的权重，否则取原始权重。
     */
    private Integer effectiveWeight(RuleHit hit) {
        if (hit == null) {
            return null;
        }
        return hit.getAdjustedWeight() != null ? hit.getAdjustedWeight() : hit.getWeight();
    }

    /**
     * 判断 W-M4 是否为背景噪音。
     * 当 W-M4 存在且同时有其他实质风险规则（W-M1、W-M2、W-M3）命中时，
     * W-M4 很可能是因为大家都用了同一个标准模板，不应作为主要风险依据。
     */
    private boolean isW_M4Noise(List<RuleHit> hits) {
        return isNoiseRule(hits, "W-M4", W_M4_NOISE_COMPETITORS);
    }

    /**
     * 判断 W-P4 是否为背景噪音。
     * 当 W-P4 与更具体的文本或规则共存时，
     * 说明 W-P4 很可能是段落中的标准化风险描述，不应在融合层指向上盖过更具体的规则。
     */
    private boolean isW_P4Noise(List<RuleHit> hits) {
        return isNoiseRule(hits, "W-P4", W_P4_NOISE_COMPETITORS);
    }

    /**
     * 对指定规则进行噪音筛选。
     * 当某个规则同时被更具体的规则命中时，将其权重降低到用于融合判定的低优先级水平。
     * 注意：此方法会直接修改传入的 RuleHit 的 adjustedWeight。
     */
    private boolean isNoiseRule(List<RuleHit> hits, String targetRuleCode, Set<String> competitorRules) {
        boolean hasTargetRule = false;
        boolean hasSignificantCompetitor = false;

        for (RuleHit hit : hits) {
            String ruleCode = hit.getRuleCode();
            if (targetRuleCode.equals(ruleCode)) {
                hasTargetRule = true;
                continue;
            }
            if (ruleCode != null && competitorRules.contains(ruleCode)) {
                Integer weight = effectiveWeight(hit);
                if (weight != null && weight >= 50) {
                    hasSignificantCompetitor = true;
                    break;
                }
            }
        }

        return hasTargetRule && hasSignificantCompetitor;
    }

    /**
     * 对 W-M4 噪音进行衰减处理。
     * 通过降低 W-M4 的 effectiveWeight 来减少其对最终分数的贡献。
     */
    private void applyW_M4NoiseReduction(List<RuleHit> hits) {
        if (!isW_M4Noise(hits)) {
            return;
        }

        for (RuleHit hit : hits) {
            if ("W-M4".equals(hit.getRuleCode())) {
                Integer originalWeight = effectiveWeight(hit);
                if (originalWeight != null) {
                    int reducedWeight = (int) Math.max(1, originalWeight * W_M4_NOISE_DECAY_FACTOR);
                    hit.setAdjustedWeight(reducedWeight);
                }
            }
        }
    }

    /**
     * 对 W-P4 噪音进行衰减处理。
     */
    private void applyW_P4NoiseReduction(List<RuleHit> hits) {
        if (!isW_P4Noise(hits)) {
            return;
        }

        for (RuleHit hit : hits) {
            if ("W-P4".equals(hit.getRuleCode())) {
                Integer originalWeight = effectiveWeight(hit);
                if (originalWeight != null) {
                    int reducedWeight = (int) Math.max(1, originalWeight * W_P4_NOISE_DECAY_FACTOR);
                    hit.setAdjustedWeight(reducedWeight);
                }
            }
        }
    }
}
