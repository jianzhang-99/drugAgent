package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.RiskFusionResult;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.workflow.EvidenceAssemblyResult;
import com.liang.drugagent.domain.workflow.EvidenceGroup;
import com.liang.drugagent.domain.workflow.EvidenceItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EvidenceAssemblerService {

    public EvidenceAssemblyResult assemble(List<RuleHit> hits,
                                           List<ExemptionHit> exemptionHits,
                                           RiskFusionResult fusionResult) {
        EvidenceAssemblyResult result = new EvidenceAssemblyResult();
        List<EvidenceGroup> groups = new ArrayList<>();

        groups.add(buildFusionGroup(fusionResult));
        groups.addAll(buildRuleGroups(hits));
        if (exemptionHits != null && !exemptionHits.isEmpty()) {
            groups.add(buildExemptionGroup(exemptionHits));
        }

        result.setGroups(groups);
        result.setFlatItems(flatten(groups));
        return result;
    }

    private EvidenceGroup buildFusionGroup(RiskFusionResult fusionResult) {
        EvidenceGroup group = new EvidenceGroup();
        group.setGroupKey("risk_fusion");
        group.setTitle("Risk Fusion");
        group.setSource("risk-fusion");
        group.setSummary(fusionResult == null
                ? "No fusion result available."
                : fusionResult.getSummary());

        if (fusionResult != null) {
            group.getItems().add(new EvidenceItem(
                    "fusion_score",
                    "score=" + fusionResult.getScore() + ", level=" + fusionResult.getRiskLevel()
                            + ", reasons=" + String.join(",", fusionResult.getReasonCodes()),
                    "risk-fusion"
            ));
        }
        return group;
    }

    private List<EvidenceGroup> buildRuleGroups(List<RuleHit> hits) {
        if (hits == null || hits.isEmpty()) {
            EvidenceGroup group = new EvidenceGroup();
            group.setGroupKey("rule_hits");
            group.setTitle("Rule Hits");
            group.setSource("rule-engine");
            group.setSummary("No retained high-risk rule hits.");
            group.getItems().add(new EvidenceItem(
                    "rule_scan_result",
                    "No contact reuse, team overlap, abnormal pricing, or other high-risk hits were retained.",
                    "rule-engine"
            ));
            return List.of(group);
        }

        Map<String, List<RuleHit>> grouped = new LinkedHashMap<>();
        for (RuleHit hit : hits) {
            grouped.computeIfAbsent(resolveGroupKey(hit), ignored -> new ArrayList<>()).add(hit);
        }

        List<EvidenceGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<RuleHit>> entry : grouped.entrySet()) {
            groups.add(buildRuleGroup(entry.getKey(), entry.getValue()));
        }
        return groups;
    }

    private EvidenceGroup buildRuleGroup(String groupKey, List<RuleHit> hits) {
        EvidenceGroup group = new EvidenceGroup();
        group.setGroupKey(groupKey);
        group.setTitle(resolveGroupTitle(groupKey));
        group.setSource("rule-engine");
        group.setSummary("retainedHits=" + hits.size() + ", topRule="
                + hits.stream().findFirst().map(RuleHit::getRuleName).orElse("unknown"));

        for (RuleHit hit : hits) {
            group.getItems().add(new EvidenceItem(
                    hit.getRuleName(),
                    buildRuleContent(hit),
                    "rule-engine"
            ));
        }
        return group;
    }

    private EvidenceGroup buildExemptionGroup(List<ExemptionHit> exemptionHits) {
        EvidenceGroup group = new EvidenceGroup();
        group.setGroupKey("exemptions");
        group.setTitle("Exemptions");
        group.setSource("exemption-engine");
        group.setSummary("processed=" + exemptionHits.size());

        for (ExemptionHit hit : exemptionHits) {
            group.getItems().add(new EvidenceItem(
                    hit.getRuleName(),
                    hit.getAction() + ", " + hit.getReason()
                            + ", weight " + hit.getBeforeWeight() + " -> " + hit.getAfterWeight(),
                    "exemption-engine"
            ));
        }
        return group;
    }

    private List<EvidenceItem> flatten(List<EvidenceGroup> groups) {
        return groups.stream()
                .flatMap(group -> group.getItems().stream())
                .collect(Collectors.toList());
    }

    private String buildRuleContent(RuleHit hit) {
        StringBuilder builder = new StringBuilder();
        builder.append(hit.getTriggerSummary());
        builder.append(" | weight=").append(effectiveWeight(hit));
        if (hit.getDocumentIds() != null && !hit.getDocumentIds().isEmpty()) {
            builder.append(" | docs=").append(String.join(",", hit.getDocumentIds()));
        }
        if (hit.getEvidences() != null && !hit.getEvidences().isEmpty()) {
            String anchors = hit.getEvidences().stream()
                    .map(this::summarizeEvidence)
                    .filter(text -> !text.isBlank())
                    .distinct()
                    .limit(3)
                    .collect(Collectors.joining("; "));
            if (!anchors.isBlank()) {
                builder.append(" | evidence=").append(anchors);
            }
        }
        if (Boolean.TRUE.equals(hit.getExempted()) && hit.getExemptionReason() != null && !hit.getExemptionReason().isBlank()) {
            builder.append(" | exemption=").append(hit.getExemptionReason());
        }
        return builder.toString();
    }

    private String summarizeEvidence(RuleEvidence evidence) {
        if (evidence == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (evidence.getDocumentId() != null) {
            parts.add(evidence.getDocumentId());
        }
        if (evidence.getChapterPath() != null) {
            parts.add(evidence.getChapterPath());
        }
        if (evidence.getMatchedValue() != null) {
            String value = evidence.getMatchedValue().length() > 40
                    ? evidence.getMatchedValue().substring(0, 40)
                    : evidence.getMatchedValue();
            parts.add(value);
        }
        return String.join(" / ", parts);
    }

    private String resolveGroupKey(RuleHit hit) {
        String ruleCode = hit == null || hit.getRuleCode() == null ? "" : hit.getRuleCode();
        if (ruleCode.startsWith("W-M1")) {
            return "pricing";
        }
        if (ruleCode.startsWith("W-M2")) {
            return "contact";
        }
        if (ruleCode.startsWith("W-M3")) {
            return "team";
        }
        if (ruleCode.startsWith("W-P")) {
            return "plagiarism";
        }
        return "other";
    }

    private String resolveGroupTitle(String groupKey) {
        return switch (groupKey) {
            case "pricing" -> "Pricing Signals";
            case "contact" -> "Contact Signals";
            case "team" -> "Team Signals";
            case "plagiarism" -> "Similarity Signals";
            default -> "Other Signals";
        };
    }

    private Integer effectiveWeight(RuleHit hit) {
        if (hit == null) {
            return null;
        }
        return hit.getAdjustedWeight() != null ? hit.getAdjustedWeight() : hit.getWeight();
    }
}
