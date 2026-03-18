package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.RiskFusionResult;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderCase;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.domain.workflow.EvidenceAssemblyResult;
import com.liang.drugagent.domain.workflow.EvidenceGroup;
import com.liang.drugagent.domain.workflow.EvidenceItem;
import com.liang.drugagent.domain.workflow.ReviewReport;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报告生成服务。
 *
 * <p>负责将风险融合结果和证据编排结果组装成统一的结构化报告对象，
 * 供工作流返回、页面展示和后续导出复用。</p>
 *
 * @author liangjiajian
 */
@Service
public class ReportGenerationService {

    public ReviewReport generate(TenderReviewData data,
                                 List<RuleHit> rawHits,
                                 List<RuleHit> effectiveHits,
                                 List<ExemptionHit> exemptionHits,
                                 RiskFusionResult fusionResult,
                                 EvidenceAssemblyResult evidenceAssemblyResult) {
        ReviewReport report = new ReviewReport();
        TenderCase tenderCase = data == null ? null : data.getACase();
        report.setCaseId(tenderCase == null ? null : tenderCase.getCaseId());
        report.setScene(tenderCase == null ? "tender_review" : tenderCase.getScene());
        report.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        report.setOverview(buildOverview(data, rawHits, effectiveHits, exemptionHits, fusionResult, evidenceAssemblyResult));
        report.setRiskItems(buildRiskItems(effectiveHits, fusionResult, evidenceAssemblyResult));
        report.setManagementSummary(buildManagementSummary(data, fusionResult, effectiveHits, exemptionHits));
        report.setRecommendedActions(buildRecommendedActions(fusionResult, effectiveHits, exemptionHits));
        report.setExplanations(buildExplanations(fusionResult, evidenceAssemblyResult, effectiveHits, exemptionHits));
        return report;
    }

    public String buildAnswer(ReviewReport report) {
        if (report == null || report.getOverview() == null) {
            return "Structured tender review completed, but no report summary is available yet.";
        }
        ReviewReport.Overview overview = report.getOverview();
        List<ReviewReport.RiskItem> items = report.getRiskItems() == null ? List.of() : report.getRiskItems();
        String topRisk = items.isEmpty() ? "No major risk themes were retained." : items.get(0).getTitle();
        return "Reviewed " + safeNumber(overview.getDocumentCount()) + " documents, retained "
                + safeNumber(overview.getEffectiveHitCount()) + " high-risk hits"
                + (safeNumber(overview.getExemptionCount()) > 0 ? ", and downgraded " + overview.getExemptionCount() + " hits by exemption" : "")
                + ". Overall risk=" + fallback(overview.getRiskLevel(), "UNKNOWN")
                + ", score=" + safeNumber(overview.getScore())
                + ". Top focus: " + topRisk + ".";
    }

    private ReviewReport.Overview buildOverview(TenderReviewData data,
                                                List<RuleHit> rawHits,
                                                List<RuleHit> effectiveHits,
                                                List<ExemptionHit> exemptionHits,
                                                RiskFusionResult fusionResult,
                                                EvidenceAssemblyResult evidenceAssemblyResult) {
        ReviewReport.Overview overview = new ReviewReport.Overview();
        overview.setDocumentCount(data == null || data.getDocuments() == null ? 0 : data.getDocuments().size());
        overview.setRawHitCount(rawHits == null ? 0 : rawHits.size());
        overview.setEffectiveHitCount(effectiveHits == null ? 0 : effectiveHits.size());
        overview.setExemptionCount(exemptionHits == null ? 0 : exemptionHits.size());
        overview.setEvidenceGroupCount(evidenceAssemblyResult == null || evidenceAssemblyResult.getGroups() == null
                ? 0 : evidenceAssemblyResult.getGroups().size());
        overview.setEvidenceItemCount(evidenceAssemblyResult == null || evidenceAssemblyResult.getFlatItems() == null
                ? 0 : evidenceAssemblyResult.getFlatItems().size());
        overview.setScore(fusionResult == null ? 0 : fusionResult.getScore());
        overview.setRiskLevel(fusionResult == null ? "UNKNOWN" : fusionResult.getRiskLevel());
        overview.setSummary(fusionResult == null ? "No risk fusion summary available." : fusionResult.getSummary());
        return overview;
    }

    private List<ReviewReport.RiskItem> buildRiskItems(List<RuleHit> effectiveHits,
                                                       RiskFusionResult fusionResult,
                                                       EvidenceAssemblyResult evidenceAssemblyResult) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            ReviewReport.RiskItem item = new ReviewReport.RiskItem();
            item.setRiskType("overall");
            item.setRiskLevel(fusionResult == null ? "LOW" : fusionResult.getRiskLevel());
            item.setTitle("未发现保留的高风险命中");
            item.setSummary("规则扫描后未保留高风险命中，当前结果更适合作为低风险或待补充样本的复核基线。");
            item.getReasonCodes().addAll(fusionResult == null ? List.of() : fusionResult.getReasonCodes());
            item.getRecommendations().add("抽样复核关键章节，确认是否存在规则暂未覆盖的异常。");
            return List.of(item);
        }

        List<RuleHit> sortedHits = new ArrayList<>(effectiveHits);
        sortedHits.sort(Comparator.comparing(this::effectiveWeight, Comparator.nullsLast(Comparator.reverseOrder())));
        List<EvidenceItem> evidenceItems = evidenceAssemblyResult == null ? List.of() : evidenceAssemblyResult.getFlatItems();

        List<ReviewReport.RiskItem> riskItems = new ArrayList<>();
        for (RuleHit hit : sortedHits.stream().limit(3).toList()) {
            ReviewReport.RiskItem item = new ReviewReport.RiskItem();
            item.setRiskType(fallback(hit.getRiskType(), "collusion"));
            item.setRiskLevel(resolveItemRiskLevel(hit, fusionResult));
            item.setTitle(fallback(hit.getRuleName(), fallback(hit.getRuleCode(), "未知风险主题")));
            item.setSummary(buildRiskItemSummary(hit));
            item.getReasonCodes().addAll(resolveReasonCodes(hit, fusionResult));
            item.getEvidenceTitles().addAll(resolveEvidenceTitles(hit, evidenceItems));
            item.getRecommendations().addAll(resolveRecommendations(hit));
            riskItems.add(item);
        }
        return riskItems;
    }

    private List<String> buildManagementSummary(TenderReviewData data,
                                                RiskFusionResult fusionResult,
                                                List<RuleHit> effectiveHits,
                                                List<ExemptionHit> exemptionHits) {
        int documentCount = data == null || data.getDocuments() == null ? 0 : data.getDocuments().size();
        int hitCount = effectiveHits == null ? 0 : effectiveHits.size();
        int exemptionCount = exemptionHits == null ? 0 : exemptionHits.size();

        List<String> summary = new ArrayList<>();
        summary.add("本次共审查 " + documentCount + " 份文档，综合风险等级为 "
                + fallback(fusionResult == null ? null : fusionResult.getRiskLevel(), "UNKNOWN")
                + "，融合分值为 " + safeNumber(fusionResult == null ? null : fusionResult.getScore()) + "。");
        if (hitCount > 0) {
            summary.add("系统保留了 " + hitCount + " 条高风险命中，建议优先复核权重最高的风险主题和跨文档共现证据。");
        } else {
            summary.add("当前未保留高风险命中，建议将结果作为低风险基线，并对关键章节进行抽样复核。");
        }
        if (exemptionCount > 0) {
            summary.add("存在 " + exemptionCount + " 条命中被免责或降权，复核时应同时关注免责依据是否充分。");
        }
        return summary;
    }

    private List<String> buildRecommendedActions(RiskFusionResult fusionResult,
                                                 List<RuleHit> effectiveHits,
                                                 List<ExemptionHit> exemptionHits) {
        List<String> actions = new ArrayList<>();
        String riskLevel = fusionResult == null ? null : fusionResult.getRiskLevel();
        if ("HIGH".equals(riskLevel)) {
            actions.add("优先复核联系人、核心团队和报价章节，确认是否存在协同行为证据链。");
            actions.add("针对命中的高权重规则，回看原始文档锚点并保留人工复核结论。");
        } else if ("MEDIUM".equals(riskLevel)) {
            actions.add("先核查跨文档重复字段和章节，再决定是否升级为人工重点复核。");
        } else {
            actions.add("保留当前报告作为初筛结果，并结合业务经验抽样检查重点章节。");
        }
        if (effectiveHits != null && effectiveHits.stream().anyMatch(hit -> "W-M1".equals(hit.getRuleCode()) || fallback(hit.getRuleCode(), "").startsWith("W-M1"))) {
            actions.add("对报价异常相关命中，建议复核报价明细表和关键参数对应关系。");
        }
        if (exemptionHits != null && !exemptionHits.isEmpty()) {
            actions.add("对已免责命中，建议复核模板引用、法规引用或低风险章节降权是否合理。");
        }
        return actions.stream().distinct().toList();
    }

    private java.util.Map<String, String> buildExplanations(RiskFusionResult fusionResult,
                                                            EvidenceAssemblyResult evidenceAssemblyResult,
                                                            List<RuleHit> effectiveHits,
                                                            List<ExemptionHit> exemptionHits) {
        java.util.Map<String, String> explanations = new java.util.LinkedHashMap<>();
        explanations.put("overall", fusionResult == null
                ? "暂无综合风险说明。"
                : "综合风险等级为 " + fallback(fusionResult.getRiskLevel(), "UNKNOWN")
                + "，主要基于 " + summarizeReasonCodes(fusionResult.getReasonCodes()) + "。");
        explanations.put("evidence", buildEvidenceExplanation(evidenceAssemblyResult));
        explanations.put("exemption", exemptionHits == null || exemptionHits.isEmpty()
                ? "当前未触发免责逻辑。"
                : "共有 " + exemptionHits.size() + " 条命中被免责或降权，复核时应同时查看免责原因与原始命中。");
        explanations.put("focus", buildFocusExplanation(effectiveHits));
        return explanations;
    }

    private String buildEvidenceExplanation(EvidenceAssemblyResult evidenceAssemblyResult) {
        if (evidenceAssemblyResult == null || evidenceAssemblyResult.getGroups() == null || evidenceAssemblyResult.getGroups().isEmpty()) {
            return "当前没有可展示的证据分组。";
        }
        List<EvidenceGroup> groups = evidenceAssemblyResult.getGroups();
        return "当前共生成 " + groups.size() + " 个证据分组，覆盖风险融合、规则命中和免责说明，可用于页面展示和人工复核回链。";
    }

    private String buildFocusExplanation(List<RuleHit> effectiveHits) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return "当前没有保留的重点风险主题。";
        }
        return "建议优先关注 "
                + effectiveHits.stream()
                .sorted(Comparator.comparing(this::effectiveWeight, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .map(hit -> fallback(hit.getRuleName(), hit.getRuleCode()))
                .collect(Collectors.joining("、"))
                + "。";
    }

    private String buildRiskItemSummary(RuleHit hit) {
        StringBuilder builder = new StringBuilder();
        builder.append(fallback(hit.getTriggerSummary(), "命中高风险规则"));
        if (effectiveWeight(hit) != null) {
            builder.append("；权重=").append(effectiveWeight(hit));
        }
        if (hit.getDocumentIds() != null && !hit.getDocumentIds().isEmpty()) {
            builder.append("；涉及文档=").append(String.join(",", hit.getDocumentIds()));
        }
        return builder.toString();
    }

    private List<String> resolveReasonCodes(RuleHit hit, RiskFusionResult fusionResult) {
        Set<String> reasonCodes = new LinkedHashSet<>();
        if (hit != null && hit.getRuleCode() != null) {
            reasonCodes.add(hit.getRuleCode());
        }
        if (fusionResult != null && fusionResult.getReasonCodes() != null) {
            reasonCodes.addAll(fusionResult.getReasonCodes());
        }
        return new ArrayList<>(reasonCodes);
    }

    private List<String> resolveEvidenceTitles(RuleHit hit, List<EvidenceItem> evidenceItems) {
        if (hit == null || evidenceItems == null || evidenceItems.isEmpty()) {
            return List.of();
        }
        return evidenceItems.stream()
                .filter(item -> item.getTitle() != null && item.getTitle().equals(hit.getRuleName()))
                .map(item -> item.getTitle() + ": " + item.getContent())
                .limit(2)
                .collect(Collectors.toList());
    }

    private List<String> resolveRecommendations(RuleHit hit) {
        List<String> recommendations = new ArrayList<>();
        String ruleCode = hit == null ? null : hit.getRuleCode();
        if (ruleCode == null) {
            recommendations.add("回看关联锚点和原始片段，确认命中是否具备完整证据链。");
            return recommendations;
        }
        if (ruleCode.startsWith("W-M1")) {
            recommendations.add("复核报价梯度、分项单价和总价之间的联动关系。");
        } else if (ruleCode.startsWith("W-M2")) {
            recommendations.add("复核联系人、电话、邮箱等字段是否存在近邻或同源特征。");
        } else if (ruleCode.startsWith("W-M3")) {
            recommendations.add("复核核心团队成员、角色和履历是否在多份标书中重复出现。");
        } else if (ruleCode.startsWith("W-P")) {
            recommendations.add("复核高相似段落是否属于模板内容，必要时扩大比对范围。");
        } else {
            recommendations.add("回看关联锚点和原始片段，确认命中是否具备完整证据链。");
        }
        return recommendations;
    }

    private String resolveItemRiskLevel(RuleHit hit, RiskFusionResult fusionResult) {
        Integer weight = effectiveWeight(hit);
        if (weight != null && weight >= 85) {
            return "HIGH";
        }
        if (weight != null && weight >= 60) {
            return "MEDIUM";
        }
        return fusionResult == null ? "LOW" : fallback(fusionResult.getRiskLevel(), "LOW");
    }

    private Integer effectiveWeight(RuleHit hit) {
        if (hit == null) {
            return null;
        }
        return hit.getAdjustedWeight() != null ? hit.getAdjustedWeight() : hit.getWeight();
    }

    private int safeNumber(Integer number) {
        return number == null ? 0 : number;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String summarizeReasonCodes(List<String> reasonCodes) {
        if (reasonCodes == null || reasonCodes.isEmpty()) {
            return "已保留的规则命中和证据编排结果";
        }
        return reasonCodes.stream()
                .map(code -> code.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("、"));
    }
}
