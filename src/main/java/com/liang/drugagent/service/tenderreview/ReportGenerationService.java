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
import com.liang.drugagent.domain.tenderreview.TenderDocument;
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
        report.setMarkdownContent(renderMarkdownReport(report, data, evidenceAssemblyResult, exemptionHits));
        return report;
    }

    public String buildAnswer(ReviewReport report) {
        if (report == null || report.getOverview() == null) {
            return "标书结构化审查已完成，但暂无报告摘要。";
        }
        ReviewReport.Overview overview = report.getOverview();
        List<ReviewReport.RiskItem> items = report.getRiskItems() == null ? List.of() : report.getRiskItems();
        String topRisk = items.isEmpty() ? "未发现关键风险主题。" : items.get(0).getTitle();
        return "本次共审查 " + safeNumber(overview.getDocumentCount()) + " 份文档，提取到 "
                + safeNumber(overview.getEffectiveHitCount()) + " 条高风险命中"
                + (safeNumber(overview.getExemptionCount()) > 0 ? "，另有 " + overview.getExemptionCount() + " 条命中被豁免/降权" : "")
                + "。整体风险等级为 " + translateRiskLevel(fallback(overview.getRiskLevel(), "UNKNOWN"))
                + "，分值 " + safeNumber(overview.getScore())
                + "。重点关注: " + topRisk + "。完整审查报告已生成。";
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
        overview.setSummary(fusionResult == null ? "暂无风险融合详细摘要。" : fusionResult.getSummary());
        return overview;
    }

    private List<ReviewReport.RiskItem> buildRiskItems(List<RuleHit> effectiveHits,
                                                       RiskFusionResult fusionResult,
                                                       EvidenceAssemblyResult evidenceAssemblyResult) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            ReviewReport.RiskItem item = new ReviewReport.RiskItem();
            item.setRiskType("overall");
            item.setRiskLevel(translateRiskLevel(fusionResult == null ? "LOW" : fusionResult.getRiskLevel()));
            item.setTitle("未发现保留的高风险命中");
            item.setSummary("规则扫描后未保留高风险命中，当前结果显示该批标书在规则维度偏差较小。");
            item.getReasonCodes().addAll(fusionResult == null ? List.of() : fusionResult.getReasonCodes());
            item.getRecommendations().add("建议抽样复核关键章节，确认是否存在规则暂未覆盖的隐蔽异常。");
            return List.of(item);
        }

        List<RuleHit> sortedHits = new ArrayList<>(effectiveHits);
        sortedHits.sort(Comparator.comparing(this::effectiveWeight, Comparator.nullsLast(Comparator.reverseOrder())));
        List<EvidenceItem> evidenceItems = evidenceAssemblyResult == null ? List.of() : evidenceAssemblyResult.getFlatItems();

        List<ReviewReport.RiskItem> riskItems = new ArrayList<>();
        for (RuleHit hit : sortedHits.stream().limit(3).toList()) {
            ReviewReport.RiskItem item = new ReviewReport.RiskItem();
            item.setRiskType(fallback(hit.getRiskType(), "collusion"));
            item.setRiskLevel(translateRiskLevel(resolveItemRiskLevel(hit, fusionResult)));
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
        summary.add("本次共审查 " + documentCount + " 份投标文档，综合风险评级为【"
                + translateRiskLevel(fallback(fusionResult == null ? null : fusionResult.getRiskLevel(), "UNKNOWN"))
                + "】，融合探测分值为 " + safeNumber(fusionResult == null ? null : fusionResult.getScore()) + "。");
        if (hitCount > 0) {
            summary.add("系统共探测到 " + hitCount + " 条实质性风险线索，建议重点关注关联度最高的前序证据。");
        } else {
            summary.add("当前审查规则下未保留高权重风险项，可作为低风险基线参考。");
        }
        if (exemptionCount > 0) {
            summary.add("另有 " + exemptionCount + " 个命中项触发了豁免逻辑（如模板引用或法规引用），已在报告中做降权处理。");
        }
        return summary;
    }

    private List<String> buildRecommendedActions(RiskFusionResult fusionResult,
                                                 List<RuleHit> effectiveHits,
                                                 List<ExemptionHit> exemptionHits) {
        List<String> actions = new ArrayList<>();
        String riskLevel = fusionResult == null ? null : fusionResult.getRiskLevel();
        if ("HIGH".equals(riskLevel) || "高风险".equals(riskLevel)) {
            actions.add("立即复核联系人、核心团队和报价清单，判定是否存在围标实锤证据。");
            actions.add("针对高权重偏移项，手动导出原文比对表进行跨行验证。");
        } else if ("MEDIUM".equals(riskLevel) || "中风险".equals(riskLevel)) {
            actions.add("抽查重复字段涉及的章节，评估是否属于投标主体间的高度协同。");
        } else {
            actions.add("保留此报告作为合规初查记录，建议对核心技术方案进行例行人工审核。");
        }
        if (effectiveHits != null && effectiveHits.stream().anyMatch(hit -> fallback(hit.getRuleCode(), "").startsWith("W-M1"))) {
            actions.add("针对报价相关异常，请核对各投标人报价明细是否出现非对称但高度同构的梯度。");
        }
        if (exemptionHits != null && !exemptionHits.isEmpty()) {
            actions.add("审查豁免项逻辑是否被滥用，特别是行业通用表达是否确实属于不可避免的重复。");
        }
        return actions.stream().distinct().toList();
    }

    private java.util.Map<String, String> buildExplanations(RiskFusionResult fusionResult,
                                                            EvidenceAssemblyResult evidenceAssemblyResult,
                                                            List<RuleHit> effectiveHits,
                                                            List<ExemptionHit> exemptionHits) {
        java.util.Map<String, String> explanations = new java.util.LinkedHashMap<>();
        explanations.put("overall", fusionResult == null
                ? "暂无详细综合风险解释。"
                : "综合风险等级为 " + translateRiskLevel(fallback(fusionResult.getRiskLevel(), "UNKNOWN"))
                + "，主要判罚依据为: " + summarizeReasonCodes(fusionResult.getReasonCodes()) + "。");
        explanations.put("evidence", buildEvidenceExplanation(evidenceAssemblyResult));
        explanations.put("exemption", exemptionHits == null || exemptionHits.isEmpty()
                ? "当前审查任务未触发任何豁免机制。"
                : "共有 " + exemptionHits.size() + " 条记录符合豁免规则（如法律引用、标准引用等），已自动从高风险清单中剔除。");
        explanations.put("focus", buildFocusExplanation(effectiveHits));
        return explanations;
    }

    private String buildEvidenceExplanation(EvidenceAssemblyResult evidenceAssemblyResult) {
        if (evidenceAssemblyResult == null || evidenceAssemblyResult.getGroups() == null || evidenceAssemblyResult.getGroups().isEmpty()) {
            return "暂无有效证据链条分组展示。";
        }
        List<EvidenceGroup> groups = evidenceAssemblyResult.getGroups();
        return "系统已自动将零散特征聚合为 " + groups.size() + " 个证据组，可直接回溯至文档的具体页码和段落。";
    }

    private String buildFocusExplanation(List<RuleHit> effectiveHits) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return "本次审查暂无特别需要聚焦的单项规则主题。";
        }
        return "建议优先复核以下具有最高关联度的风险项: "
                + effectiveHits.stream()
                .sorted(Comparator.comparing(this::effectiveWeight, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .map(hit -> fallback(hit.getRuleName(), hit.getRuleCode()))
                .collect(Collectors.joining("、"))
                + "。";
    }

    private String buildRiskItemSummary(RuleHit hit) {
        StringBuilder builder = new StringBuilder();
        builder.append(fallback(hit.getTriggerSummary(), "触发单项规则命中"));
        if (effectiveWeight(hit) != null) {
            builder.append("；评估权重=").append(effectiveWeight(hit));
        }
        if (hit.getDocumentIds() != null && !hit.getDocumentIds().isEmpty()) {
            builder.append("；涉及标书=").append(String.join(", ", hit.getDocumentIds()));
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
                .map(item -> "证据点: " + item.getContent())
                .limit(2)
                .collect(Collectors.toList());
    }

    private List<String> resolveRecommendations(RuleHit hit) {
        List<String> recommendations = new ArrayList<>();
        String ruleCode = hit == null ? null : hit.getRuleCode();
        if (ruleCode == null) {
            recommendations.add("手动对照原始文档，重点查验该风险片段在上下文中的位置及合理性。");
            return recommendations;
        }
        if (ruleCode.startsWith("W-M1")) {
            recommendations.add("对比各投标主体的分项报价单，确认单价、利润点及总报价是否存在非正常线性重合。");
        } else if (ruleCode.startsWith("W-M2")) {
            recommendations.add("排查联系人、办公地址及企业资质信息是否在不同投标人中交叉复用。");
        } else if (ruleCode.startsWith("W-M3")) {
            recommendations.add("针对核心团队重合风险，确认该人员是否为唯一授权代表人或属于法律允许外的交叉兼职。");
        } else if (ruleCode.startsWith("W-P")) {
            recommendations.add("利用全文查重工具核实相似段落是否超出行业通用语范围。");
        } else {
            recommendations.add("深入核实关联证据链，关注该风险项是否存在协同配合的围标痕迹。");
        }
        return recommendations;
    }

    private String translateRiskLevel(String rawLevel) {
        if (rawLevel == null) return "未知";
        return switch (rawLevel.toUpperCase(Locale.ROOT)) {
            case "HIGH" -> "高风险";
            case "MEDIUM" -> "中风险";
            case "LOW" -> "低风险";
            case "UNKNOWN" -> "未知";
            default -> rawLevel;
        };
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

    private String renderMarkdownReport(ReviewReport report,
                                        TenderReviewData data,
                                        EvidenceAssemblyResult evidenceAssemblyResult,
                                        List<ExemptionHit> exemptionHits) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 标书审核报告").append("\n\n");
        builder.append("> 适用于药械招投标场景下的围标、陪标、异常相似性风险审查。")
                .append("本报告由场景一工作流自动生成，用于辅助业务负责人、评审专家及监管人员开展复核。")
                .append("\n\n");

        builder.append("## 一、报告基本信息").append("\n\n");
        builder.append("| 项目 | 内容 |").append("\n");
        builder.append("| :--- | :--- |").append("\n");
        builder.append("| 审查编号 | ").append(safeText(report.getCaseId(), "待补充")).append(" |").append("\n");
        builder.append("| 审查主体 | ").append(resolveSubmittedBy(data)).append(" |").append("\n");
        builder.append("| 审查时间 | ").append(safeText(report.getGeneratedAt(), "待补充")).append(" |").append("\n");
        builder.append("| 审查场景 | 药械招投标围标/陪标风险扫描 |").append("\n");
        builder.append("| 审查对象 | ").append(resolveProjectName(data)).append(" |").append("\n");
        builder.append("| 报告版本 | drug-agent-service-v1.0.0 |").append("\n\n");

        builder.append("---").append("\n\n");
        builder.append("## 二、审查结论").append("\n\n");
        builder.append("### 2.1 结论等级").append("\n\n");
        builder.append("- **综合风险等级**: **")
                .append(translateRiskLevel(report.getOverview() == null ? null : report.getOverview().getRiskLevel()))
                .append("**").append("\n");
        builder.append("- **风险参考分值**: ")
                .append(safeNumber(report.getOverview() == null ? null : report.getOverview().getScore()))
                .append("\n");
        builder.append("- **建议处置意见**: ")
                .append(resolveFinalAdvice(report))
                .append("\n\n");

        builder.append("### 2.2 结论摘要").append("\n\n");
        builder.append(safeText(report.getOverview() == null ? null : report.getOverview().getSummary(), "暂无结论摘要。"))
                .append("\n\n");

        builder.append("### 2.3 审查概览").append("\n\n");
        builder.append("- **比对文件数量**: ").append(safeNumber(report.getOverview() == null ? null : report.getOverview().getDocumentCount())).append(" 份").append("\n");
        builder.append("- **识别潜在线索数**: ").append(safeNumber(report.getOverview() == null ? null : report.getOverview().getRawHitCount())).append(" 条").append("\n");
        builder.append("- **确认有效风险项**: ").append(safeNumber(report.getOverview() == null ? null : report.getOverview().getEffectiveHitCount())).append(" 条").append("\n");
        builder.append("- **系统豁免或降权项**: ").append(safeNumber(report.getOverview() == null ? null : report.getOverview().getExemptionCount())).append(" 条").append("\n\n");

        builder.append("---").append("\n\n");
        builder.append("## 三、重点风险说明").append("\n\n");
        builder.append("> 本部分用于呈现需要优先关注的风险事项，按风险等级和权重排序。").append("\n\n");
        builder.append("| 序号 | 风险类型 | 风险等级 | 规则名称 | 风险说明 | 初步建议 |").append("\n");
        builder.append("| :--- | :--- | :--- | :--- | :--- | :--- |").append("\n");
        appendRiskItemRows(builder, report.getRiskItems());
        builder.append("\n");

        builder.append("### 重点风险研判").append("\n\n");
        appendRiskItemSections(builder, report.getRiskItems());

        builder.append("---").append("\n\n");
        builder.append("## 四、证据链与交叉印证").append("\n\n");
        builder.append("> 本部分用于支撑风险结论，强调来源明确、位置可定位、逻辑可解释。").append("\n\n");
        appendEvidenceSections(builder, evidenceAssemblyResult, data);

        builder.append("---").append("\n\n");
        builder.append("## 五、系统豁免与说明事项").append("\n\n");
        builder.append("> 用于披露系统已识别但经规则判定为合理重复或参考价值有限的内容。").append("\n\n");
        appendExemptionSections(builder, exemptionHits);

        builder.append("---").append("\n\n");
        builder.append("## 六、后续处置建议").append("\n\n");
        builder.append("> 建议结合项目重要程度、风险等级及评审阶段，按轻重缓急推进处置。").append("\n\n");
        appendActions(builder, report.getRecommendedActions(), report.getRiskItems());

        builder.append("\n---").append("\n\n");
        builder.append("## 七、使用说明").append("\n\n");
        builder.append("- 本报告为系统辅助审查结果，不直接替代人工认定结论。").append("\n");
        builder.append("- 报告中“风险等级”反映当前规则模型下的相对风险强弱，应结合项目背景综合判断。").append("\n");
        builder.append("- 若证据链不完整或样本不足，建议以“进一步核查”作为主要处置方向，避免直接作出结论性认定。").append("\n\n");
        builder.append("---").append("\n\n");
        builder.append("*生成引擎: ReportGenerationService (Chinese Edition)*").append("\n");
        return builder.toString();
    }

    private void appendRiskItemRows(StringBuilder builder, List<ReviewReport.RiskItem> riskItems) {
        if (riskItems == null || riskItems.isEmpty()) {
            builder.append("| 1 | 综合研判 | 低风险 | 未发现保留的高风险命中 | 当前规则扫描未保留高权重异常。 | 建议抽样复核关键章节。 |").append("\n");
            return;
        }
        int index = 1;
        for (ReviewReport.RiskItem item : riskItems) {
            builder.append("| ").append(index++).append(" | ")
                    .append(safeText(item.getRiskType(), "综合研判")).append(" | ")
                    .append(safeText(item.getRiskLevel(), "未知")).append(" | ")
                    .append(safeText(item.getTitle(), "未命名风险项")).append(" | ")
                    .append(toTableCell(item.getSummary())).append(" | ")
                    .append(toTableCell(firstOf(item.getRecommendations(), "建议人工复核原始文档内容。")))
                    .append(" |").append("\n");
        }
    }

    private void appendRiskItemSections(StringBuilder builder, List<ReviewReport.RiskItem> riskItems) {
        if (riskItems == null || riskItems.isEmpty()) {
            builder.append("#### [风险点-01] 未发现高风险命中").append("\n");
            builder.append("- **涉及对象**: 待补充").append("\n");
            builder.append("- **异常表现**: 当前规则扫描未识别出需要重点保留的异常。").append("\n");
            builder.append("- **判定依据**: 基于现有规则、权重和豁免逻辑综合判定。").append("\n");
            builder.append("- **影响评估**: 可作为低风险参考，但不替代人工复核。").append("\n");
            builder.append("- **处置建议**: 对核心章节进行抽样核验。").append("\n\n");
            return;
        }
        int index = 1;
        for (ReviewReport.RiskItem item : riskItems) {
            builder.append("#### [风险点-").append(String.format("%02d", index++)).append("] ")
                    .append(safeText(item.getTitle(), "未命名风险项")).append("\n");
            builder.append("- **涉及对象**: ").append(resolveRelatedParties(item)).append("\n");
            builder.append("- **异常表现**: ").append(safeText(item.getSummary(), "待补充")).append("\n");
            builder.append("- **判定依据**: ").append(resolveBasis(item)).append("\n");
            builder.append("- **影响评估**: ").append(resolveImpact(item)).append("\n");
            builder.append("- **处置建议**: ").append(firstOf(item.getRecommendations(), "建议结合证据链开展人工重点复核。")).append("\n\n");
        }
    }

    private void appendEvidenceSections(StringBuilder builder,
                                        EvidenceAssemblyResult evidenceAssemblyResult,
                                        TenderReviewData data) {
        List<EvidenceGroup> groups = evidenceAssemblyResult == null ? List.of() : evidenceAssemblyResult.getGroups();
        if (groups == null || groups.isEmpty()) {
            builder.append("### [证据组-01] 暂无可展示证据组").append("\n\n");
            builder.append("- **证据类型**: 系统说明").append("\n");
            builder.append("- **命中内容**: `暂无可回溯的证据链数据`").append("\n");
            builder.append("- **涉及文档 1**: 待补充").append("\n");
            builder.append("- **涉及文档 2**: 待补充").append("\n");
            builder.append("- **交叉印证说明**: 当前流程未形成可展示的证据组。").append("\n");
            builder.append("- **复核关注点**: 建议结合规则命中结果回查原始文档。").append("\n\n");
            return;
        }

        int index = 1;
        for (EvidenceGroup group : groups.stream().limit(3).toList()) {
            List<EvidenceItem> items = group.getItems() == null ? List.of() : group.getItems();
            builder.append("### [证据组-").append(String.format("%02d", index++)).append("] ")
                    .append(safeText(group.getTitle(), "未命名证据组")).append("\n\n");
            builder.append("- **证据类型**: ").append(safeText(group.getSource(), safeText(group.getGroupKey(), "系统聚合"))).append("\n");
            builder.append("- **命中内容**: `").append(safeInlineContent(firstEvidenceContent(items))).append("`").append("\n");
            builder.append("- **涉及文档 1**: ").append(resolveEvidenceDocument(data, items, 0)).append("\n");
            builder.append("- **涉及文档 2**: ").append(resolveEvidenceDocument(data, items, 1)).append("\n");
            builder.append("- **交叉印证说明**: ").append(safeText(group.getSummary(), "系统已将同类异常线索聚合为同一证据组，可支持人工回溯。")).append("\n");
            builder.append("- **复核关注点**: 建议结合文件形成时间、编制主体、报价逻辑及附件一致性进一步复核。").append("\n\n");
        }
    }

    private void appendExemptionSections(StringBuilder builder, List<ExemptionHit> exemptionHits) {
        if (exemptionHits == null || exemptionHits.isEmpty()) {
            builder.append("### [豁免项-01] 暂无系统豁免项").append("\n\n");
            builder.append("- **豁免原因**: 当前任务未触发豁免规则。").append("\n");
            builder.append("- **处理方式**: 无").append("\n");
            builder.append("- **说明**: 当前有效风险项均已纳入正式审查结论。").append("\n\n");
            return;
        }

        int index = 1;
        for (ExemptionHit hit : exemptionHits.stream().limit(3).toList()) {
            builder.append("### [豁免项-").append(String.format("%02d", index++)).append("] ")
                    .append(safeText(hit.getRuleName(), safeText(hit.getRuleCode(), "未命名豁免项"))).append("\n\n");
            builder.append("- **豁免原因**: ").append(safeText(hit.getReason(), "符合行业通用模板、法规政策引用或标准格式复用特征。")).append("\n");
            builder.append("- **处理方式**: 权重调整为 ").append(safeNumber(hit.getAfterWeight())).append("\n");
            builder.append("- **说明**: ").append(resolveExemptionNote(hit)).append("\n\n");
        }
    }

    private void appendActions(StringBuilder builder,
                               List<String> actions,
                               List<ReviewReport.RiskItem> riskItems) {
        List<String> resolved = (actions == null || actions.isEmpty()) ? List.of(
                "对重点风险项开展人工复核。",
                "必要时补充关联主体和原文比对材料。",
                "对审查结果归档留存。"
        ) : actions;
        String topRisk = (riskItems == null || riskItems.isEmpty()) ? "重点风险项" : safeText(riskItems.get(0).getTitle(), "重点风险项");
        int index = 1;
        for (String action : resolved) {
            String priority = switch (index) {
                case 1 -> "高";
                case 2, 3 -> "中";
                default -> "低";
            };
            String content = index == 1 ? normalizeFirstAction(action, topRisk) : safeText(action, "待补充");
            builder.append(index++).append(". **优先级").append(priority).append("**: ").append(content).append("\n");
        }
    }

    private String resolveSubmittedBy(TenderReviewData data) {
        TenderCase tenderCase = data == null ? null : data.getACase();
        return safeText(tenderCase == null ? null : tenderCase.getSubmittedBy(), "系统自动触发");
    }

    private String resolveProjectName(TenderReviewData data) {
        if (data == null || data.getDocuments() == null || data.getDocuments().isEmpty()) {
            return "待补充";
        }
        return data.getDocuments().stream()
                .map(TenderDocument::getDocumentName)
                .filter(name -> name != null && !name.isBlank())
                .limit(2)
                .collect(Collectors.joining(" / "));
    }

    private String resolveFinalAdvice(ReviewReport report) {
        if (report == null || report.getRecommendedActions() == null || report.getRecommendedActions().isEmpty()) {
            return "建议结合人工复核结果，决定是否进入重点核查流程。";
        }
        return report.getRecommendedActions().get(0);
    }

    private String resolveRelatedParties(ReviewReport.RiskItem item) {
        if (item == null || item.getEvidenceTitles() == null || item.getEvidenceTitles().isEmpty()) {
            return "待补充";
        }
        return item.getEvidenceTitles().stream().limit(2).collect(Collectors.joining("；"));
    }

    private String resolveBasis(ReviewReport.RiskItem item) {
        if (item == null || item.getReasonCodes() == null || item.getReasonCodes().isEmpty()) {
            return "基于文本相似特征、结构重复特征、报价异常或关联关系线索综合判断。";
        }
        return "基于规则编码 " + String.join("、", item.getReasonCodes()) + " 及关联证据链综合判断。";
    }

    private String resolveImpact(ReviewReport.RiskItem item) {
        String riskLevel = item == null ? null : item.getRiskLevel();
        if ("高风险".equals(riskLevel)) {
            return "该异常可能显著影响投标文件独立性判断，建议纳入人工重点复核。";
        }
        if ("中风险".equals(riskLevel)) {
            return "该异常可能反映投标主体之间存在较强协同迹象，建议进一步核查。";
        }
        return "该异常当前影响程度相对有限，建议结合上下文审慎判断。";
    }

    private String firstEvidenceContent(List<EvidenceItem> items) {
        if (items == null || items.isEmpty()) {
            return "暂无证据内容";
        }
        return safeText(items.get(0).getContent(), "暂无证据内容");
    }

    private String resolveEvidenceDocument(TenderReviewData data, List<EvidenceItem> items, int index) {
        List<TenderDocument> documents = data == null ? List.of() : data.getDocuments();
        if (documents == null || documents.isEmpty()) {
            return "待补充";
        }
        TenderDocument document = documents.get(Math.min(index, documents.size() - 1));
        return "**" + safeText(document.getDocumentName(), safeText(document.getFilename(), safeText(document.getDocumentId(), "未知文档")))
                + "**，定位信息待补充";
    }

    private String resolveExemptionNote(ExemptionHit hit) {
        if (hit == null) {
            return "该项已纳入系统降权处理，不单独作为风险结论依据。";
        }
        return "动作=" + safeText(hit.getAction(), "降权处理")
                + "，原始权重=" + safeNumber(hit.getBeforeWeight())
                + "，调整后权重=" + safeNumber(hit.getAfterWeight())
                + "。";
    }

    private String normalizeFirstAction(String action, String topRisk) {
        if (action == null || action.isBlank()) {
            return "对“" + topRisk + "”相关线索启动人工复核。";
        }
        return action.contains(topRisk) ? action : "对“" + topRisk + "”相关线索启动人工复核，" + action;
    }

    private String firstOf(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return safeText(values.get(0), fallback);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String toTableCell(String value) {
        return safeText(value, "待补充").replace("|", "\\|").replace("\n", "<br>");
    }

    private String safeInlineContent(String value) {
        return safeText(value, "暂无证据内容").replace("`", "'");
    }
}
