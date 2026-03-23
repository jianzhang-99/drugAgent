package com.liang.drugagent.scenes.tender_review.application.services;

import com.liang.drugagent.scenes.tender_review.domain.model.ExemptionHit;
import com.liang.drugagent.scenes.tender_review.domain.model.RiskFusionResult;
import com.liang.drugagent.scenes.tender_review.domain.model.RuleEvidence;
import com.liang.drugagent.scenes.tender_review.domain.model.RuleHit;
import com.liang.drugagent.core.domain.model.EvidenceAssemblyResult;
import com.liang.drugagent.core.domain.model.EvidenceGroup;
import com.liang.drugagent.core.domain.model.EvidenceItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 证据组装服务。
 * 负责将规则命中项、免责记录及风险评分聚合转换为可供前端直接渲染的“证据树”结构。
 *
 * @author liangjiajian
 */
@Service
public class EvidenceAssemblerService {

    /**
     * 将散落的执行记录组装为结构化的证据链。
     *
     * @param hits 风险命中项列表
     * @param exemptionHits 已免责记录（若有）
     * @param fusionResult 风险融合汇总
     * @return 包含证据分组（Group）和扁平列表（Flat）的结果集
     */
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
        group.setTitle("风险融合");
        group.setSource("risk-fusion");
        group.setSummary(fusionResult == null
                ? "暂无融合结果。"
                : fusionResult.getSummary());

        if (fusionResult != null) {
            String reasons = fusionResult.getReasonCodes() == null || fusionResult.getReasonCodes().isEmpty()
                    ? "无"
                    : String.join(",", fusionResult.getReasonCodes());
            group.getItems().add(new EvidenceItem(
                    "fusion_score",
                    "分值=" + fusionResult.getScore() + "，等级=" + translateLevel(fusionResult.getRiskLevel())
                            + "，原因=" + reasons,
                    "risk-fusion"
            ));
        }
        return group;
    }

    private List<EvidenceGroup> buildRuleGroups(List<RuleHit> hits) {
        if (hits == null || hits.isEmpty()) {
            EvidenceGroup group = new EvidenceGroup();
            group.setGroupKey("rule_hits");
            group.setTitle("规则命中");
            group.setSource("rule-engine");
            group.setSummary("无保留的高风险规则命中。");
            group.getItems().add(new EvidenceItem(
                    "rule_scan_result",
                    "未检测到联系人复用、团队重叠、报价异常或其他高风险命中。",
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
        group.setSummary("保留命中=" + hits.size() + "，首要规则="
                + hits.stream().findFirst().map(RuleHit::getRuleName).orElse("未知"));

        for (RuleHit hit : hits) {
            group.getItems().add(new EvidenceItem(
                    resolveEvidenceItemTitle(hit),
                    buildRuleContent(hit),
                    "rule-engine"
            ));
        }
        return group;
    }

    private EvidenceGroup buildExemptionGroup(List<ExemptionHit> exemptionHits) {
        EvidenceGroup group = new EvidenceGroup();
        group.setGroupKey("exemptions");
        group.setTitle("豁免项");
        group.setSource("exemption-engine");
        group.setSummary("已处理=" + exemptionHits.size() + "项");

        for (ExemptionHit hit : exemptionHits) {
            group.getItems().add(new EvidenceItem(
                    resolveDisplayLabel(hit == null ? null : hit.getRuleName()),
                    hit.getAction() + "，" + hit.getReason()
                            + "，权重 " + hit.getBeforeWeight() + " -> " + hit.getAfterWeight(),
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
        builder.append(" | 权重=").append(effectiveWeight(hit));
        if (hit.getDocumentIds() != null && !hit.getDocumentIds().isEmpty()) {
            builder.append(" | 文档=").append(String.join(",", hit.getDocumentIds()));
        }
        if (hit.getEvidences() != null && !hit.getEvidences().isEmpty()) {
            String anchors = hit.getEvidences().stream()
                    .map(this::summarizeEvidence)
                    .filter(text -> !text.isBlank())
                    .distinct()
                    .limit(3)
                    .collect(Collectors.joining("; "));
            if (!anchors.isBlank()) {
                builder.append(" | 证据=").append(anchors);
            }
        }
        if (Boolean.TRUE.equals(hit.getExempted()) && hit.getExemptionReason() != null && !hit.getExemptionReason().isBlank()) {
            builder.append(" | 豁免原因=").append(hit.getExemptionReason());
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
            case "pricing" -> "报价信号";
            case "contact" -> "联系人信号";
            case "team" -> "团队信号";
            case "plagiarism" -> "相似度信号";
            default -> "其他信号";
        };
    }

    private Integer effectiveWeight(RuleHit hit) {
        if (hit == null) {
            return null;
        }
        return hit.getAdjustedWeight() != null ? hit.getAdjustedWeight() : hit.getWeight();
    }

    private String translateLevel(String level) {
        if (level == null) return "未知";
        return switch (level.toUpperCase()) {
            case "HIGH" -> "高风险";
            case "MEDIUM" -> "中风险";
            case "LOW" -> "低风险";
            default -> level;
        };
    }

    private String resolveEvidenceItemTitle(RuleHit hit) {
        if (hit == null) {
            return "规则命中";
        }
        String label = resolveDisplayLabel(hit.getRuleName());
        if (label != null && !label.isBlank()) {
            return label;
        }
        return resolveDisplayLabel(hit.getRuleCode());
    }

    private String resolveDisplayLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "未命名证据";
        }
        return switch (raw) {
            case "fusion_score" -> "融合评分";
            case "rule_scan_result" -> "规则扫描结果";
            case "quote_gradient" -> "报价梯度异常";
            case "contact_nearby", "contact_proximity" -> "联系人近邻";
            case "team_overlap", "core_team_overlap" -> "核心团队重叠";
            case "proposal_copy", "proposal_plagiarism" -> "方案内容雷同";
            case "template_homology" -> "模板同源";
            case "rare_typo_cooccurrence" -> "罕见错误共现";
            case "error_replication" -> "错误复现";
            case "service_commitment" -> "服务承诺雷同";
            case "implementation_method" -> "实施方法雷同";
            case "case_data_plagiarism" -> "案例数据复用";
            case "risk_identification" -> "风险识别异常";
            default -> {
                if (raw.startsWith("W-M1")) {
                    yield "报价梯度异常";
                }
                if (raw.startsWith("W-M2")) {
                    yield "联系人近邻";
                }
                if (raw.startsWith("W-M3")) {
                    yield "核心团队重叠";
                }
                if (raw.startsWith("W-P1")) {
                    yield "技术方案雷同";
                }
                if (raw.startsWith("W-P3")) {
                    yield "服务承诺雷同";
                }
                if (raw.startsWith("W-P5")) {
                    yield "错误复现";
                }
                yield raw;
            }
        };
    }
}
