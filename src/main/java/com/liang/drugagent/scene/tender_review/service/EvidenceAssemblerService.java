package com.liang.drugagent.scene.tender_review.service;

import com.liang.drugagent.scene.tender_review.model.ExemptionHit;
import com.liang.drugagent.scene.tender_review.model.RiskFusionResult;
import com.liang.drugagent.scene.tender_review.model.RuleEvidence;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.shared.model.EvidenceAssemblyResult;
import com.liang.drugagent.shared.model.EvidenceGroup;
import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.report.Page4DetailComparison;
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
                                           RiskFusionResult fusionResult,
                                           Map<String, String> docIdToName) {
        EvidenceAssemblyResult result = new EvidenceAssemblyResult();
        List<EvidenceGroup> groups = new ArrayList<>();

        groups.add(buildFusionGroup(fusionResult));
        groups.addAll(buildRuleGroups(hits, docIdToName));
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

    private List<EvidenceGroup> buildRuleGroups(List<RuleHit> hits, Map<String, String> docIdToName) {
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
            groups.add(buildRuleGroup(entry.getKey(), entry.getValue(), docIdToName));
        }
        return groups;
    }

    private EvidenceGroup buildRuleGroup(String groupKey, List<RuleHit> hits, Map<String, String> docIdToName) {
        EvidenceGroup group = new EvidenceGroup();
        group.setGroupKey(groupKey);
        group.setTitle(resolveGroupTitle(groupKey));
        group.setSource("rule-engine");
        group.setSummary("保留命中=" + hits.size() + "，首要规则="
                + hits.stream().findFirst().map(RuleHit::getRuleName).orElse("未知"));

        for (RuleHit hit : hits) {
            group.getItems().add(new EvidenceItem(
                    resolveEvidenceItemTitle(hit),
                    buildRuleContent(hit, docIdToName),
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

    private String buildRuleContent(RuleHit hit, Map<String, String> docIdToName) {
        StringBuilder builder = new StringBuilder();
        builder.append(hit.getTriggerSummary());
        if (hit.getEvidences() != null && !hit.getEvidences().isEmpty()) {
            String anchors = hit.getEvidences().stream()
                    .map(e -> summarizeEvidence(e, docIdToName))
                    .filter(text -> !text.isBlank())
                    .distinct()
                    .limit(3)
                    .collect(Collectors.joining("；"));
            if (!anchors.isBlank()) {
                builder.append("。典型证据：").append(anchors);
            }
        }
        if (Boolean.TRUE.equals(hit.getExempted()) && hit.getExemptionReason() != null && !hit.getExemptionReason().isBlank()) {
            builder.append("（已豁免：" + hit.getExemptionReason() + "）");
        }
        return builder.toString();
    }

    private String summarizeEvidence(RuleEvidence evidence, Map<String, String> docIdToName) {
        if (evidence == null) {
            return "";
        }
        // 用可读文件名替代原始 ID
        String docLabel = evidence.getDocumentId();
        if (docIdToName != null && docIdToName.containsKey(evidence.getDocumentId())) {
            docLabel = docIdToName.get(evidence.getDocumentId());
        }
        // 只保留章节名和关键值，格式：章节名｜关键值（文档名）
        String chapter = evidence.getChapterPath() != null ? evidence.getChapterPath() : "";
        String value = evidence.getMatchedValue();
        if (value != null && value.length() > 30) {
            value = value.substring(0, 30) + "…";
        }
        StringBuilder sb = new StringBuilder();
        if (!chapter.isBlank()) {
            sb.append("《").append(chapter).append("》");
        }
        if (value != null && !value.isBlank()) {
            sb.append("出现异常值：").append(value);
        }
        if (!docLabel.isBlank()) {
            sb.append("（来源：").append(docLabel).append("）");
        }
        return sb.toString();
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

    /**
     * 按风险类型分组证据，用于报告第2页风险总览。
     *
     * @param hits 有效命中列表
     * @param fusionResult 风险融合结果
     * @param docIdToName 文档ID到名称的映射
     * @return 按风险类型分组的Map：pricing/team/plagiarism/template/auxiliary
     */
    public Map<String, List<RuleHit>> groupByRiskType(List<RuleHit> hits,
                                                      RiskFusionResult fusionResult,
                                                      Map<String, String> docIdToName) {
        Map<String, List<RuleHit>> grouped = new LinkedHashMap<>();
        grouped.put("pricing", new ArrayList<>());
        grouped.put("team", new ArrayList<>());
        grouped.put("plagiarism", new ArrayList<>());
        grouped.put("template", new ArrayList<>());
        grouped.put("auxiliary", new ArrayList<>());

        if (hits == null || hits.isEmpty()) {
            return grouped;
        }

        for (RuleHit hit : hits) {
            String type = resolveReportGroupKey(hit);
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(hit);
        }
        return grouped;
    }

    /**
     * 生成详细比对数据，用于报告第4页。
     *
     * @param hits 有效命中列表
     * @param docIdToName 文档ID到名称的映射
     * @return 包含报价对比、团队对比和文本高亮的比对数据
     */
    public Page4DetailComparison buildDetailComparison(List<RuleHit> hits,
                                                        Map<String, String> docIdToName) {
        Page4DetailComparison comparison = new Page4DetailComparison();

        // 构建报价对比
        comparison.setPriceComparison(buildPriceComparison(hits, docIdToName));

        // 构建团队对比
        comparison.setTeamComparison(buildTeamComparison(hits, docIdToName));

        // 构建文本高亮
        comparison.setTextHighlights(buildTextHighlights(hits, docIdToName));

        return comparison;
    }

    private String resolveReportGroupKey(RuleHit hit) {
        if (hit == null || hit.getRuleCode() == null) {
            return "auxiliary";
        }
        String ruleCode = hit.getRuleCode();
        if (ruleCode.startsWith("W-M1")) {
            return "pricing";
        }
        if (ruleCode.startsWith("W-M2")) {
            return "team";
        }
        if (ruleCode.startsWith("W-M3")) {
            return "team";
        }
        if (ruleCode.startsWith("W-P1") || ruleCode.startsWith("W-P2") || ruleCode.startsWith("W-P3")) {
            return "text_similarity";
        }
        if (ruleCode.startsWith("W-P4") || ruleCode.startsWith("W-P5")) {
            return "template";
        }
        return "auxiliary";
    }

    private Page4DetailComparison.PriceComparison buildPriceComparison(List<RuleHit> hits,
                                                                        Map<String, String> docIdToName) {
        List<Page4DetailComparison.PriceRow> rows = new ArrayList<>();
        List<String> headers = List.of("报价项", "文档A", "文档B", "差异", "判定");

        List<RuleHit> pricingHits = (hits == null ? List.<RuleHit>of() : hits).stream()
                .filter(h -> h.getRuleCode() != null && h.getRuleCode().startsWith("W-M1"))
                .toList();

        for (RuleHit hit : pricingHits) {
            List<RuleEvidence> evidences = hit.getEvidences() == null ? List.of() : hit.getEvidences();
            RuleEvidence evidenceA = evidences.size() > 0 ? evidences.get(0) : null;
            RuleEvidence evidenceB = evidences.size() > 1 ? evidences.get(1) : null;

            Page4DetailComparison.PriceRow row = Page4DetailComparison.PriceRow.builder()
                    .item(resolvePriceItemName(hit, evidenceA))
                    .docA(resolveEvidenceValue(evidenceA, docIdToName))
                    .docB(resolveEvidenceValue(evidenceB, docIdToName))
                    .diff(resolveDiffText(hit))
                    .verdict(resolveDetailVerdict(hit))
                    .build();
            rows.add(row);
        }

        return Page4DetailComparison.PriceComparison.builder()
                .headers(headers)
                .rows(rows)
                .build();
    }

    private Page4DetailComparison.TeamComparison buildTeamComparison(List<RuleHit> hits,
                                                                      Map<String, String> docIdToName) {
        List<Page4DetailComparison.TeamRow> rows = new ArrayList<>();
        List<String> headers = List.of("角色", "文档A", "文档B", "判定");

        List<RuleHit> teamHits = (hits == null ? List.<RuleHit>of() : hits).stream()
                .filter(h -> h.getRuleCode() != null && (h.getRuleCode().startsWith("W-M2") || h.getRuleCode().startsWith("W-M3")))
                .toList();

        for (RuleHit hit : teamHits) {
            List<RuleEvidence> evidences = hit.getEvidences() == null ? List.of() : hit.getEvidences();
            RuleEvidence evidenceA = evidences.size() > 0 ? evidences.get(0) : null;
            RuleEvidence evidenceB = evidences.size() > 1 ? evidences.get(1) : null;

            Page4DetailComparison.TeamRow row = Page4DetailComparison.TeamRow.builder()
                    .role(resolveTeamRole(hit, evidenceA))
                    .docA(resolveEvidenceValue(evidenceA, docIdToName))
                    .docB(resolveEvidenceValue(evidenceB, docIdToName))
                    .verdict(resolveDetailVerdict(hit))
                    .build();
            rows.add(row);
        }

        return Page4DetailComparison.TeamComparison.builder()
                .headers(headers)
                .rows(rows)
                .build();
    }

    private List<Page4DetailComparison.TextHighlight> buildTextHighlights(List<RuleHit> hits,
                                                                          Map<String, String> docIdToName) {
        List<Page4DetailComparison.TextHighlight> highlights = new ArrayList<>();

        List<RuleHit> plagiarismHits = (hits == null ? List.<RuleHit>of() : hits).stream()
                .filter(h -> h.getRuleCode() != null && h.getRuleCode().startsWith("W-P"))
                .toList();

        for (RuleHit hit : plagiarismHits) {
            String category = resolveDisplayLabel(hit.getRuleName());
            String textA = "";
            String textB = "";
            if (hit.getEvidences() != null && hit.getEvidences().size() >= 2) {
                textA = summarizeEvidenceText(hit.getEvidences().get(0));
                textB = summarizeEvidenceText(hit.getEvidences().get(1));
            }

            Page4DetailComparison.TextHighlight highlight = Page4DetailComparison.TextHighlight.builder()
                    .category(category)
                    .textA(textA)
                    .textB(textB)
                    .similarity(hit.getConfidence() != null
                            ? String.format("%.0f%%", hit.getConfidence() * 100)
                            : "-")
                    .verdict(resolveTextVerdict(hit))
                    .analysis(resolveTextAnalysis(hit))
                    .build();
            highlights.add(highlight);
        }

        return highlights;
    }

    private String resolveDocName(String docId, Map<String, String> docIdToName) {
        if (docId == null) {
            return "-";
        }
        if (docIdToName != null && docIdToName.containsKey(docId)) {
            return docIdToName.get(docId);
        }
        return docId.length() > 8 ? docId.substring(0, 8) + "..." : docId;
    }

    private String summarizeEvidenceText(RuleEvidence evidence) {
        if (evidence == null) {
            return "-";
        }
        String value = evidence.getMatchedValue();
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 50 ? value.substring(0, 50) + "..." : value;
    }

    private String resolveEvidenceValue(RuleEvidence evidence, Map<String, String> docIdToName) {
        if (evidence == null) {
            return "-";
        }
        String value = evidence.getMatchedValue();
        if (value != null && !value.isBlank()) {
            return value.length() > 28 ? value.substring(0, 28) + "..." : value;
        }
        return resolveDocName(evidence.getDocumentId(), docIdToName);
    }

    private String resolvePriceItemName(RuleHit hit, RuleEvidence evidence) {
        if (evidence != null && evidence.getChapterPath() != null && !evidence.getChapterPath().isBlank()) {
            return evidence.getChapterPath();
        }
        return resolveDisplayLabel(hit == null ? null : hit.getRuleName());
    }

    private String resolveTeamRole(RuleHit hit, RuleEvidence evidence) {
        if (evidence != null && evidence.getChapterPath() != null && !evidence.getChapterPath().isBlank()) {
            return evidence.getChapterPath();
        }
        return resolveDisplayLabel(hit == null ? null : hit.getRuleName());
    }

    private String resolveDiffText(RuleHit hit) {
        if (hit == null) {
            return "-";
        }
        if (hit.getMatchedValue() != null && !hit.getMatchedValue().isBlank()) {
            return hit.getMatchedValue();
        }
        return effectiveWeight(hit) != null ? "权重 " + effectiveWeight(hit) : "-";
    }

    private String resolveDetailVerdict(RuleHit hit) {
        Integer weight = effectiveWeight(hit);
        if (weight != null && weight >= 85) {
            return "高度异常";
        }
        if (weight != null && weight >= 60) {
            return "异常";
        }
        return "提示";
    }

    private String resolveTextVerdict(RuleHit hit) {
        if (hit == null || hit.getConfidence() == null) {
            return resolveDetailVerdict(hit);
        }
        if (hit.getConfidence() >= 0.9) {
            return "高度相似";
        }
        if (hit.getConfidence() >= 0.7) {
            return "中度相似";
        }
        return "存在相似";
    }

    private String resolveTextAnalysis(RuleHit hit) {
        return hit == null || hit.getTriggerSummary() == null || hit.getTriggerSummary().isBlank()
                ? "该片段存在相似表达，建议结合上下文继续复核。"
                : hit.getTriggerSummary();
    }
}
