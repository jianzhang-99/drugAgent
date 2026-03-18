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

        int maxWeight = hits.stream()
                .map(this::effectiveWeight)
                .filter(weight -> weight != null)
                .max(Comparator.naturalOrder())
                .orElse(0);
        int score = maxWeight;

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
            if ("HIGH".equals(hit.getPriority()) && effectiveWeight(hit) != null && effectiveWeight(hit) >= 85) {
                hasHighPriorityHardRule = true;
            }
            if (hit.getEvidences() != null) {
                evidenceCount += hit.getEvidences().size();
            }
        }

        List<String> reasonCodes = new ArrayList<>();
        if (hasHighPriorityHardRule) {
            score += 8;
            reasonCodes.add("HIGH_PRIORITY_RULE");
        }
        if (ruleCodes.size() >= 2) {
            score += 7;
            reasonCodes.add("MULTI_RULE_CO_OCCURRENCE");
        }
        if (hits.size() >= 3) {
            score += 5;
            reasonCodes.add("MULTI_HIT_ACCUMULATION");
        }
        if (documentIds.size() >= 2) {
            score += 5;
            reasonCodes.add("CROSS_DOCUMENT_EVIDENCE");
        }
        if (evidenceCount >= 4) {
            score += 5;
            reasonCodes.add("EVIDENCE_SUFFICIENT");
        }
        if (!exemptions.isEmpty()) {
            score -= Math.min(exemptions.size() * 3, 12);
            reasonCodes.add("EXEMPTION_DOWNGRADE");
        }

        score = Math.max(0, Math.min(score, 100));
        result.setScore(score);
        result.setRiskLevel(resolveRiskLevel(score, hasHighPriorityHardRule));
        result.setReasonCodes(reasonCodes);
        result.setSummary(buildSummary(score, hits.size(), exemptions.size(), ruleCodes));
        return result;
    }

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

    private String buildSummary(int score, int hitCount, int exemptionCount, Set<String> ruleCodes) {
        return "Risk fusion score=" + score
                + ", retainedHits=" + hitCount
                + ", ruleTypes=" + ruleCodes.size()
                + (exemptionCount > 0 ? ", exemptions=" + exemptionCount : "");
    }

    private Integer effectiveWeight(RuleHit hit) {
        if (hit == null) {
            return null;
        }
        return hit.getAdjustedWeight() != null ? hit.getAdjustedWeight() : hit.getWeight();
    }
}
