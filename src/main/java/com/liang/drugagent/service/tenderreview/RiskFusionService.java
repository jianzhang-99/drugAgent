package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.RiskFusionResult;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 风险融合服务。
 * 负责依据各项规则的命中权重、命中丰富度、多源证据关联性进行加权计算。
 * 该服务将离散的规则命中碎片融合成单一的、可解释的综合风险评分及分级。
 *
 * @author liangjiajian
 */
@Service
public class RiskFusionService {

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
        // 1. 处理无命中项或全部免责的情况
        if (hits.isEmpty()) {
            result.setRiskLevel("LOW");
            result.setScore(exemptions.isEmpty() ? 0 : 20);
            result.setSummary(exemptions.isEmpty()
                    ? "No retained high-risk hits after rule scan."
                    : "Raw hits were observed, but all were downgraded or filtered by exemption.");
            if (!exemptions.isEmpty()) {
                result.setReasonCodes(List.of("EXEMPTION_DOWNGRADE"));
            }
            return result;
        }

        // 2. 取所有命中项中最高的权重作为基础分
        int maxWeight = hits.stream()
                .map(this::effectiveWeight)
                .filter(weight -> weight != null)
                .max(Comparator.naturalOrder())
                .orElse(0);
        int score = maxWeight;

        // 3. 统计特征：规则覆盖度、文档覆盖度、高优硬规则、证据丰富度
        Set<String> ruleCodes = new LinkedHashSet<>();
        Set<String> documentIds = new LinkedHashSet<>();
        boolean hasHighPriorityHardRule = false;
        int evidenceCount = 0;
        for (RuleHit hit : hits) {
            if (hit.getRuleCode() != null) {
                ruleCodes.add(hit.getRuleCode());
            }
            if (hit.getDocumentIds() != null) {
                documentIds.addAll(hit.getDocumentIds());
            }
            // 硬规则判定逻辑：高优先级且权重 >= 85
            if ("HIGH".equals(hit.getPriority()) && effectiveWeight(hit) != null && effectiveWeight(hit) >= 85) {
                hasHighPriorityHardRule = true;
            }
            if (hit.getEvidences() != null) {
                evidenceCount += hit.getEvidences().size();
            }
        }

        // 4. 多维风险累加计算
        List<String> reasonCodes = new ArrayList<>();
        // 高优规则加分
        if (hasHighPriorityHardRule) {
            score += 8;
            reasonCodes.add("HIGH_PRIORITY_RULE");
        }
        // 多规则命中加分
        if (ruleCodes.size() >= 2) {
            score += 7;
            reasonCodes.add("MULTI_RULE_CO_OCCURRENCE");
        }
        // 命中次数累积加分
        if (hits.size() >= 3) {
            score += 5;
            reasonCodes.add("MULTI_HIT_ACCUMULATION");
        }
        // 跨文档证据加分
        if (documentIds.size() >= 2) {
            score += 5;
            reasonCodes.add("CROSS_DOCUMENT_EVIDENCE");
        }
        // 证据越充分则风险确定性越高
        if (evidenceCount >= 4) {
            score += 5;
            reasonCodes.add("EVIDENCE_SUFFICIENT");
        }
        // 5. 免责项负向削减权重
        if (!exemptions.isEmpty()) {
            score -= Math.min(exemptions.size() * 3, 12);
            reasonCodes.add("EXEMPTION_DOWNGRADE");
        }

        // 6. 归一化评分 (0-100)
        score = Math.max(0, Math.min(score, 100));
        result.setScore(score);
        result.setRiskLevel(resolveRiskLevel(score, hasHighPriorityHardRule));
        result.setReasonCodes(reasonCodes);
        result.setSummary(buildSummary(score, hits.size(), exemptions.size(), ruleCodes));
        return result;
    }

    /**
     * 根据评分和是否存在硬规则判定风险等级。
     *
     * @param score 综合风险分
     * @param hasHighPriorityHardRule 是否命中高优硬规则
     * @return 风险等级 (HIGH, MEDIUM, LOW)
     */
    private String resolveRiskLevel(int score, boolean hasHighPriorityHardRule) {
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
     * 构建风险简报。
     */
    private String buildSummary(int score, int hitCount, int exemptionCount, Set<String> ruleCodes) {
        return "Risk fusion score=" + score
                + ", retainedHits=" + hitCount
                + ", ruleTypes=" + ruleCodes.size()
                + (exemptionCount > 0 ? ", exemptions=" + exemptionCount : "");
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
}
