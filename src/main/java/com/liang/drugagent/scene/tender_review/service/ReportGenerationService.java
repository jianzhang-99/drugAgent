package com.liang.drugagent.scene.tender_review.service;

import com.liang.drugagent.scene.tender_review.model.ExemptionHit;
import com.liang.drugagent.scene.tender_review.model.RiskFusionResult;
import com.liang.drugagent.scene.tender_review.model.RuleEvidence;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderCase;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.shared.model.EvidenceAssemblyResult;
import com.liang.drugagent.shared.model.EvidenceGroup;
import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.ReviewReport;
import com.liang.drugagent.shared.model.report.*;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * 生成标书审查报告。
     *
     * <p>整合审查全链路数据，生成结构化的审查报告，包含：
     * <ul>
     *   <li>概览信息（文档数、命中数、免责数）</li>
     *   <li>风险项列表</li>
     *   <li>管理建议摘要</li>
     *   <li>推荐行动</li>
     *   <li>免责说明</li>
     *   <li>Markdown 格式报告内容</li>
     * </ul>
     *
     * @param data 标书审查数据
     * @param rawHits 原始规则命中列表
     * @param effectiveHits 有效命中列表（排除免责后）
     * @param exemptionHits 免责命中列表
     * @param fusionResult 风险融合结果
     * @param evidenceAssemblyResult 证据组装结果
     * @return 结构化审查报告
     */
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
        Map<String, String> docIdToName = buildDocIdToName(data);
        report.setRiskItems(buildRiskItems(effectiveHits, fusionResult, evidenceAssemblyResult, docIdToName));
        report.setManagementSummary(buildManagementSummary(data, fusionResult, effectiveHits, exemptionHits));
        String topRisk = report.getRiskItems() != null && !report.getRiskItems().isEmpty()
                ? report.getRiskItems().get(0).getTitle() : "重点风险项";
        report.setRecommendedActions(buildRecommendedActions(fusionResult, effectiveHits, exemptionHits, topRisk));
        report.setExplanations(buildExplanations(fusionResult, evidenceAssemblyResult, effectiveHits, exemptionHits));
        report.setMarkdownContent(renderMarkdownReport(report, data, evidenceAssemblyResult, exemptionHits));
        return report;
    }

    /**
     * 根据报告构建用户可读的文本回答。
     *
     * <p>优先使用 Markdown 格式的报告内容，
     * 如果报告为空则返回默认提示文本。</p>
     *
     * @param report 审查报告
     * @return 用于展示的报告文本
     */
    public String buildAnswer(ReviewReport report) {
        if (report == null) {
            return "标书结构化审查已完成，但暂无报告内容。";
        }
        // 直接使用 renderMarkdownReport 生成的完整模板格式报告
        if (report.getMarkdownContent() != null && !report.getMarkdownContent().isBlank()) {
            return report.getMarkdownContent();
        }
        return "标书结构化审查已完成，但暂无报告内容。";
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
                                                       EvidenceAssemblyResult evidenceAssemblyResult,
                                                       Map<String, String> docIdToName) {
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
            item.setSummary(buildRiskItemSummary(hit, docIdToName));
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
                                                 List<ExemptionHit> exemptionHits,
                                                 String topRisk) {
        // 按模板生成固定的4条建议
        List<String> actions = new ArrayList<>();
        actions.add("对\"" + fallback(topRisk, "重点风险项") + "\"相关线索启动人工复核，重点核查投标文件独立编制情况及投标主体关联关系。");
        actions.add("对异常相似段落、异常一致报价或重复性附件开展专项比对，必要时补充外部佐证材料。");
        actions.add("视情况调取企业人员、联系方式、历史投标记录等关联信息，排查是否存在协同投标迹象。");
        actions.add("对当前审查结果及数字底稿进行归档留存，作为后续审计、复议或监管核查依据。");
        return actions;
    }

    private java.util.Map<String, String> buildExplanations(RiskFusionResult fusionResult,
                                                            EvidenceAssemblyResult evidenceAssemblyResult,
                                                            List<RuleHit> effectiveHits,
                                                            List<ExemptionHit> exemptionHits) {
        java.util.Map<String, String> explanations = new java.util.LinkedHashMap<>();
        explanations.put("overall", fusionResult == null
                ? "暂无详细综合风险解释。"
                : "综合风险等级判定为「" + translateRiskLevel(fallback(fusionResult.getRiskLevel(), "UNKNOWN"))
                + "」，主要基于" + summarizeReasonCodes(fusionResult.getReasonCodes()) + "等因素综合研判。");
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

    private String buildRiskItemSummary(RuleHit hit, Map<String, String> docIdToName) {
        StringBuilder builder = new StringBuilder();
        builder.append(fallback(hit.getTriggerSummary(), "系统识别到异常线索"));
        if (hit.getDocumentIds() != null && !hit.getDocumentIds().isEmpty()) {
            builder.append("，涉及").append(resolveReadableDocumentNames(hit.getDocumentIds(), docIdToName));
        }
        if (effectiveWeight(hit) != null) {
            builder.append("，风险关注度").append(riskAttentionLabel(effectiveWeight(hit)));
        }
        return builder.toString();
    }

    private String riskAttentionLabel(Integer weight) {
        if (weight == null) return "待评估";
        if (weight >= 85) return "高";
        if (weight >= 60) return "较高";
        if (weight >= 30) return "中等";
        return "较低";
    }

    private String buildCoreRiskTitle(RuleHit hit) {
        if (hit == null) {
            return "未命名风险";
        }
        String categoryKey = resolveGroupKeyForReport(hit);
        String displayLabel = resolveDisplayLabel(firstNonBlank(hit.getRuleName(), hit.getRuleCode(), ""));
        if (!displayLabel.isBlank() && !displayLabel.equals(hit.getRuleCode())) {
            return displayLabel;
        }
        return switch (categoryKey) {
            case "pricing" -> "报价异常";
            case "team" -> "核心团队重复";
            case "text_similarity" -> "关键条款相似";
            case "template" -> "模板同源";
            default -> "辅助异常线索";
        };
    }

    private String buildTopRiskSummary(RuleHit hit) {
        String summary = fallback(hit == null ? null : hit.getTriggerSummary(), "");
        if (!summary.isBlank()) {
            return summary;
        }
        return switch (resolveGroupKeyForReport(hit)) {
            case "pricing" -> "多个报价项存在异常一致性，建议优先核查报价形成依据。";
            case "team" -> "核心岗位人员存在交叉复用迹象，建议核验人员归属与授权关系。";
            case "text_similarity" -> "关键条款出现高度相似表达，建议人工对照原文复核。";
            case "template" -> "文档结构与模板特征高度接近，建议排查是否存在同源底稿。";
            default -> "存在可增强结论判断的辅助异常线索。";
        };
    }

    private String buildCategoryExplanation(String type,
                                            int hitCount,
                                            RuleHit topHit,
                                            RiskFusionResult fusionResult) {
        if (hitCount == 0) {
            return switch (type) {
                case "pricing" -> "当前未发现分项报价、总价或价差模式的明显异常。";
                case "team" -> "当前未发现核心人员重合或团队组织异常。";
                case "text_similarity" -> "当前未发现关键方案、风险条款存在高相似表达。";
                case "template" -> "当前未发现目录结构、章节顺序或版式风格的明显同源特征。";
                default -> "当前未发现足以增强结论判断的辅助异常线索。";
            };
        }

        String summary = fallback(topHit == null ? null : topHit.getTriggerSummary(),
                fallback(fusionResult == null ? null : fusionResult.getSummary(), "存在明显异常信号。"));
        return switch (type) {
            case "pricing" -> "检测到报价模式异常，共命中" + hitCount + "条线索。代表性发现：" + summary;
            case "team" -> "检测到团队或联系人信息异常，共命中" + hitCount + "条线索。代表性发现：" + summary;
            case "text_similarity" -> "检测到技术方案、服务承诺或风险条款高相似内容，共命中" + hitCount + "条线索。代表性发现：" + summary;
            case "template" -> "检测到模板同源或错误复现特征，共命中" + hitCount + "条线索。代表性发现：" + summary;
            default -> "检测到可增强综合判断的辅助异常，共命中" + hitCount + "条线索。代表性发现：" + summary;
        };
    }

    private String buildRepresentativeEvidence(RuleHit topHit) {
        if (topHit == null) {
            return "当前未提取到代表性证据。";
        }
        if (topHit.getEvidences() != null && !topHit.getEvidences().isEmpty()) {
            return topHit.getEvidences().stream()
                    .map(ev -> fallback(ev.getMatchedValue(), ev.getChapterPath()))
                    .filter(text -> text != null && !text.isBlank())
                    .map(this::trimSnippet)
                    .distinct()
                    .limit(2)
                    .collect(Collectors.joining("；"));
        }
        return fallback(topHit.getTriggerSummary(), "已命中代表性风险线索。");
    }

    private String buildCategoryAction(String type) {
        return switch (type) {
            case "pricing" -> "优先核查报价编制依据、形成过程及历史报价记录。";
            case "team" -> "核验人员社保、任职证明、授权关系及项目履历。";
            case "text_similarity" -> "人工对照原文，核查关键条款是否超出通用表述范围。";
            case "template" -> "排查是否存在同源模板、错误复现或统一底稿来源。";
            default -> "结合外围信息补强证据链，再决定是否升级处理。";
        };
    }

    private String buildEvidenceExplanation(RuleHit hit) {
        String summary = fallback(hit == null ? null : hit.getTriggerSummary(), "");
        if (!summary.isBlank()) {
            return summary;
        }
        return "该证据指向" + resolveCategoryName(resolveGroupKeyForReport(hit)) + "，需要结合原文和外围信息做进一步复核。";
    }

    private String buildEvidenceBasis(RuleHit hit, RiskFusionResult fusionResult) {
        List<String> parts = new ArrayList<>();
        if (hit != null && hit.getRuleCode() != null) {
            parts.add("系统通过「" + resolveDisplayLabel(hit.getRuleCode()) + "」规则识别");
        }
        if (hit != null && hit.getConfidence() != null && hit.getConfidence() >= 0.8) {
            parts.add("判定置信度较高");
        }
        if (fusionResult != null && fusionResult.getReasonCodes() != null && !fusionResult.getReasonCodes().isEmpty()) {
            parts.add("综合依据包括" + summarizeReasonCodes(fusionResult.getReasonCodes()));
        }
        return parts.isEmpty() ? "基于规则命中与证据片段交叉判定。" : String.join("，", parts) + "。";
    }

    private String resolveConfidence(RuleHit hit) {
        if (hit == null || hit.getConfidence() == null) {
            return effectiveWeight(hit) != null && effectiveWeight(hit) >= 85 ? "高" : "中";
        }
        if (hit.getConfidence() >= 0.8) {
            return "高";
        }
        if (hit.getConfidence() >= 0.5) {
            return "中";
        }
        return "低";
    }

    private Map<String, String> buildDocIdToName(TenderReviewData data) {
        Map<String, String> docIdToName = new LinkedHashMap<>();
        if (data == null || data.getDocuments() == null) {
            return docIdToName;
        }
        for (TenderDocument document : data.getDocuments()) {
            docIdToName.put(document.getDocumentId(), firstNonBlank(document.getDocumentName(), document.getFilename(), document.getDocumentId()));
        }
        return docIdToName;
    }

    private String resolveCategoryName(String categoryKey) {
        return switch (categoryKey) {
            case "pricing" -> "报价风险";
            case "team" -> "团队风险";
            case "text_similarity" -> "文本相似风险";
            case "template" -> "模板同源风险";
            default -> "其他辅助风险";
        };
    }

    private String normalizeRiskLevelCode(String rawLevel) {
        if (rawLevel == null || rawLevel.isBlank()) {
            return "safe";
        }
        return switch (rawLevel.toUpperCase(Locale.ROOT)) {
            case "HIGH" -> "high";
            case "MEDIUM" -> "medium";
            case "LOW" -> "low";
            case "SAFE" -> "safe";
            default -> switch (rawLevel.toLowerCase(Locale.ROOT)) {
                case "高", "高风险" -> "high";
                case "中", "中风险" -> "medium";
                case "低", "低风险" -> "low";
                default -> "safe";
            };
        };
    }

    private String toRiskLevelLabel(String levelCode) {
        return switch (normalizeRiskLevelCode(levelCode)) {
            case "high" -> "高风险";
            case "medium" -> "中风险";
            case "low" -> "低风险";
            default -> "低风险";
        };
    }

    private String trimSnippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() > 60 ? text.substring(0, 60) + "..." : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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
                .map(EvidenceItem::getContent)
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
                .map(this::translateReasonCode)
                .distinct()
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
            builder.append("- **命中内容**: ").append(safeInlineContent(firstEvidenceContent(items))).append("\n");
            builder.append("- **涉及文档 1**: ").append(resolveEvidenceDocument(data, items, 0)).append("\n");
            builder.append("- **涉及文档 2**: ").append(resolveEvidenceDocument(data, items, 1)).append("\n");
            builder.append("- **交叉印证**: ").append(safeText(group.getSummary(), "系统已将同类异常线索聚合为同一证据组，可支持人工回溯。")).append("\n");
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
            return "系统结合文本相似特征、结构重复特征、报价异常或关联关系线索进行综合研判。";
        }
        return "系统识别到" + summarizeReasonCodes(item.getReasonCodes()) + "，并结合关联证据链进行综合研判。";
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
        int before = safeNumber(hit.getBeforeWeight());
        int after = safeNumber(hit.getAfterWeight());
        if (after == 0) {
            return "该项经豁免判定后已从风险清单中移除，不影响最终结论。";
        }
        return "该项原始风险权重为 " + before + "，经豁免判定后降至 " + after + "，对最终结论影响有限。";
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

    private String resolveReadableDocumentNames(List<String> documentIds, Map<String, String> docIdToName) {
        if (documentIds == null || documentIds.isEmpty()) {
            return "待补充";
        }
        return documentIds.stream()
                .map(docId -> {
                    if (docIdToName != null && docIdToName.containsKey(docId)) {
                        return docIdToName.get(docId);
                    }
                    return docId;
                })
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private String translateReasonCode(String code) {
        if (code == null || code.isBlank()) {
            return "系统综合判断";
        }
        return switch (code) {
            case "MULTI_RULE_CO_OCCURRENCE" -> "同一批文件同时命中多类异常信号";
            case "MULTI_HIT_ACCUMULATION" -> "同类异常多次出现，说明不是单点偶发";
            case "CROSS_DOCUMENT_VALIDATION", "CROSS_DOCUMENT_EVIDENCE" -> "异常在多份文件之间形成交叉印证";
            case "EVIDENCE_SUFFICIENT" -> "当前证据数量和强度足以支撑进一步复核";
            case "HIGH_PRIORITY_RULE" -> "命中了高优先级风险规则";
            case "SYNERGY_BONUS" -> "多条异常共同增强了整体风险判断";
            case "EXEMPTION_DOWNGRADE" -> "部分线索已按模板或引用场景做降权处理";
            default -> resolveDisplayLabel(code);
        };
    }

    // ==================== 报告决策页面数据结构生成（V2 用户视角版） ====================

    private final EvidenceAssemblerService evidenceAssembler = new EvidenceAssemblerService();

    /**
     * 生成报告决策页面数据结构（V2 用户视角版）。
     *
     * <p>按照"标书审查报告原型V1"生成结构化报告数据，供前端 FormalReportDocument 直接渲染。</p>
     *
     * @param data 标书审查数据
     * @param rawHits 原始规则命中列表
     * @param effectiveHits 有效命中列表（排除免责后）
     * @param exemptionHits 免责命中列表
     * @param fusionResult 风险融合结果
     * @param evidenceAssemblyResult 证据组装结果
     * @return V2 结构的报告数据
     */
    public ReportData generateReportData(TenderReviewData data,
                                        List<RuleHit> rawHits,
                                        List<RuleHit> effectiveHits,
                                        List<ExemptionHit> exemptionHits,
                                        RiskFusionResult fusionResult,
                                        EvidenceAssemblyResult evidenceAssemblyResult) {
        ReportData reportData = new ReportData();

        // V2: 1. 总体结论
        reportData.setExecutiveSummary(buildExecutiveSummary(data, effectiveHits, fusionResult, evidenceAssemblyResult));

        // V2: 2. 风险总览
        reportData.setRiskOverview(buildRiskOverview(effectiveHits, fusionResult));

        // V2: 3. 本次比对文件
        reportData.setDocuments(buildDocuments(data));

        // V2: 4. 关键证据明细
        reportData.setEvidences(buildEvidences(effectiveHits, fusionResult, evidenceAssemblyResult, data));

        // V2: 5. 处置建议
        reportData.setActionPlan(buildActionPlan(fusionResult, effectiveHits));

        // V2: 6. 报告元信息
        reportData.setMetadata(buildMetadata(data, fusionResult));

        // 保留旧版结构以兼容
        reportData.setPage1Summary(buildPage1Summary(data, rawHits, effectiveHits, exemptionHits, fusionResult));
        reportData.setPage2RiskOverview(buildPage2RiskOverview(effectiveHits, fusionResult));
        reportData.setPage3CoreEvidence(buildPage3CoreEvidence(effectiveHits, fusionResult));
        reportData.setPage4DetailComparison(buildPage4DetailComparison(data, effectiveHits));
        reportData.setPage5ActionSuggestions(buildPage5ActionSuggestions(fusionResult, effectiveHits, exemptionHits));
        reportData.setPage6Appendix(buildPage6Appendix(rawHits, fusionResult));

        return reportData;
    }

    // ==================== V2 结构生成方法 ====================

    /**
     * 构建 V2 总体结论。
     */
    private ReportData.ExecutiveSummary buildExecutiveSummary(TenderReviewData data,
                                                             List<RuleHit> effectiveHits,
                                                             RiskFusionResult fusionResult,
                                                             EvidenceAssemblyResult evidenceAssemblyResult) {
        String riskLevelCode = normalizeRiskLevelCode(fusionResult == null ? null : fusionResult.getRiskLevel());
        String riskLevelLabel = resolveRiskLevelLabel(riskLevelCode);
        int hitCount = effectiveHits == null ? 0 : effectiveHits.size();
        int evidenceCount = evidenceAssemblyResult == null ? 0 : evidenceAssemblyResult.getGroups().size();
        int docCount = data == null || data.getDocuments() == null ? 0 : data.getDocuments().size();

        // 按原型生成完整自然语言结论
        String conclusionText = buildV2Conclusion(effectiveHits, fusionResult, evidenceAssemblyResult);

        return ReportData.ExecutiveSummary.builder()
                .conclusionText(conclusionText)
                .riskLevelLabel(riskLevelLabel)
                .metrics(ReportData.ExecutiveSummary.Metrics.builder()
                        .effectiveHits(hitCount)
                        .evidenceClusters(evidenceCount)
                        .documentCount(docCount)
                        .build())
                .reviewNote("本报告用于辅助识别标书疑似围标、串标或非独立编制风险，不直接替代最终评审结论。")
                .riskLevel(riskLevelCode)
                .riskScore(fusionResult == null ? 0 : fusionResult.getScore())
                .overallConclusion(conclusionText)
                .recommendedAction(buildV2RecommendedAction(effectiveHits, riskLevelCode))
                .build();
    }

    /**
     * 按原型生成 V2 版本的完整结论正文。
     */
    private String buildV2Conclusion(List<RuleHit> effectiveHits, RiskFusionResult fusionResult, EvidenceAssemblyResult evidenceAssemblyResult) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return "本次比对未发现明显的高风险线索，当前材料在报价结构、核心团队成员和关键条款表述上未见显著异常。";
        }

        // 统计各类型命中
        long pricingHits = effectiveHits.stream().filter(h -> "pricing".equals(resolveGroupKeyForReport(h))).count();
        long teamHits = effectiveHits.stream().filter(h -> "team".equals(resolveGroupKeyForReport(h))).count();
        long textHits = effectiveHits.stream().filter(h -> "text_similarity".equals(resolveGroupKeyForReport(h))).count();

        StringBuilder builder = new StringBuilder("本次比对发现两份投标文件");
        List<String> findings = new ArrayList<>();

        if (pricingHits > 0) {
            findings.add("报价结构出现规律性价差");
        }
        if (teamHits > 0) {
            findings.add("核心团队中多名成员重复");
        }
        if (textHits > 0) {
            findings.add("技术方案与风险应对内容存在高度相似表述");
        }

        if (!findings.isEmpty()) {
            builder.append(findings.get(0));
            for (int i = 1; i < findings.size(); i++) {
                builder.append("、").append(findings.get(i));
            }
        }

        builder.append("。上述线索共同指向两份文件可能存在非独立编制或协同编写风险。");
        builder.append("建议先暂停自动通过流程，进入人工重点复核。");

        return builder.toString();
    }

    private String buildV2RecommendedAction(List<RuleHit> effectiveHits, String riskLevelCode) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return "建议保留本次审查结果，并对关键章节进行抽样复核。";
        }
        return switch (riskLevelCode) {
            case "high" -> "建议立即启动人工复核，并优先核查报价形成依据、人员真实归属、文件编制来源和关键条款原文比对。";
            case "medium" -> "建议尽快开展人工核验，补充核查关键条款、联系人信息和历史投标记录。";
            default -> "建议纳入持续观察，必要时补充人工抽检。";
        };
    }

    /**
     * 构建 V2 风险总览。
     */
    private ReportData.RiskOverview buildRiskOverview(List<RuleHit> effectiveHits, RiskFusionResult fusionResult) {
        List<ReportData.TopRisk> topRisks = buildV2TopRisks(effectiveHits, fusionResult);
        List<ReportData.RiskDistribution> distributions = buildV2Distributions(effectiveHits);
        return ReportData.RiskOverview.builder()
                .topRisks(topRisks)
                .distributions(distributions)
                .build();
    }

    /**
     * 构建 V2 重点风险判断（最多3条）。
     */
    private List<ReportData.TopRisk> buildV2TopRisks(List<RuleHit> effectiveHits, RiskFusionResult fusionResult) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return List.of();
        }

        List<RuleHit> sortedHits = new ArrayList<>(effectiveHits);
        sortedHits.sort(Comparator.comparing(this::effectiveWeight, Comparator.nullsLast(Comparator.reverseOrder())));

        List<ReportData.TopRisk> topRisks = new ArrayList<>();
        int rank = 1;
        for (RuleHit hit : sortedHits.stream().limit(3).toList()) {
            String categoryKey = resolveGroupKeyForReport(hit);
            String riskName = buildCoreRiskTitle(hit);
            String itemLevel = normalizeRiskLevelCode(resolveItemRiskLevel(hit, fusionResult));

            topRisks.add(ReportData.TopRisk.builder()
                    .rank(rank++)
                    .ruleCode(hit.getRuleCode())
                    .riskName(riskName)
                    .level(itemLevel)
                    .riskDesc(buildV2RiskDesc(hit, categoryKey))
                    .keyFact(buildV2KeyFact(hit, categoryKey))
                    .whyReview(buildV2WhyReview(categoryKey))
                    .action(buildV2Action(categoryKey))
                    .riskLevel(itemLevel)
                    .riskType(categoryKey)
                    .build());
        }
        return topRisks;
    }

    private String buildV2RiskDesc(RuleHit hit, String categoryKey) {
        return switch (categoryKey) {
            case "pricing" -> "两份投标文件中多个报价项目存在规律性价差，差异呈现固定模式，不符合独立报价中常见的随机波动特征。";
            case "team" -> "两份投标文件中发现多名核心团队成员重复，涉及项目经理、技术负责人等关键角色。";
            case "text_similarity" -> "两份投标文件在风险识别、应对措施和部分实施方案描述上存在高度相似表达。";
            case "template" -> "两份投标文件在目录结构、章节顺序或版式风格上表现出明显的同源特征。";
            default -> "系统识别到需要关注的异常线索，建议人工复核确认。";
        };
    }

    private String buildV2KeyFact(RuleHit hit, String categoryKey) {
        String trigger = safeText(hit.getTriggerSummary(), "");
        if (!trigger.isBlank()) {
            return trigger;
        }
        return switch (categoryKey) {
            case "pricing" -> "多个报价项呈现接近或固定差异。";
            case "team" -> "同一人员出现在不同投标主体的核心团队配置中。";
            case "text_similarity" -> "多处段落在内容结构、句式和业务表达上接近。";
            case "template" -> "文档结构和排版风格高度一致。";
            default -> "存在需要复核的异常线索。";
        };
    }

    private String buildV2WhyReview(String categoryKey) {
        return switch (categoryKey) {
            case "pricing" -> "如果报价差异长期呈规律性分布，可能说明报价不是由各投标主体独立测算形成，而是存在统一编制、协同调整或陪标报价的可能。";
            case "team" -> "核心团队成员重复可能影响投标主体独立履约能力判断，也可能提示主体之间存在人员借用、资质共享或协同投标风险。";
            case "text_similarity" -> "如果相似内容超出行业通用表达范围，可能说明文件存在同源底稿、统一模板或协同编写情况。";
            case "template" -> "模板同源可能反映投标主体使用了相同的编制来源，存在串通投标的风险。";
            default -> "需要人工复核以确认是否存在实质性风险。";
        };
    }

    private String buildV2Action(String categoryKey) {
        return switch (categoryKey) {
            case "pricing" -> "复核报价明细表、报价形成依据、报价编制人员和历史报价记录。";
            case "team" -> "核查人员社保归属、劳动关系、授权文件、任职单位和项目履历。";
            case "text_similarity" -> "人工对照原文，判断相似内容是否属于行业通用描述，必要时比对历史投标文件。";
            case "template" -> "排查是否存在同源模板、错误复现或统一底稿来源。";
            default -> "结合外围材料补强证据链，再决定是否升级处理。";
        };
    }

    /**
     * 构建 V2 风险分布（只展示有发现的风险方向）。
     */
    private List<ReportData.RiskDistribution> buildV2Distributions(List<RuleHit> effectiveHits) {
        List<ReportData.RiskDistribution> distributions = new ArrayList<>();

        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return distributions;
        }

        // 报价风险
        long pricingCount = effectiveHits.stream().filter(h -> "pricing".equals(resolveGroupKeyForReport(h))).count();
        if (pricingCount > 0) {
            distributions.add(ReportData.RiskDistribution.builder()
                    .riskType("报价异常")
                    .found(true)
                    .foundDescription("发现明显异常")
                    .needReview(true)
                    .needReviewText("必须复核")
                    .brief("多个报价项呈现规律性价差，需核查报价形成过程")
                    .hitCount((int) pricingCount)
                    .level("high")
                    .build());
        }

        // 团队风险
        long teamCount = effectiveHits.stream().filter(h -> "team".equals(resolveGroupKeyForReport(h))).count();
        if (teamCount > 0) {
            distributions.add(ReportData.RiskDistribution.builder()
                    .riskType("核心团队重复")
                    .found(true)
                    .foundDescription("发现明显异常")
                    .needReview(true)
                    .needReviewText("必须复核")
                    .brief("两份文件中出现多名核心人员重合")
                    .hitCount((int) teamCount)
                    .level("high")
                    .build());
        }

        // 文本相似
        long textCount = effectiveHits.stream().filter(h -> "text_similarity".equals(resolveGroupKeyForReport(h))).count();
        if (textCount > 0) {
            distributions.add(ReportData.RiskDistribution.builder()
                    .riskType("关键条款相似")
                    .found(true)
                    .foundDescription("发现明显异常")
                    .needReview(true)
                    .needReviewText("必须复核")
                    .brief("技术方案、风险应对等内容存在高度相似表达")
                    .hitCount((int) textCount)
                    .level("high")
                    .build());
        }

        // 模板同源
        long templateCount = effectiveHits.stream().filter(h -> "template".equals(resolveGroupKeyForReport(h))).count();
        if (templateCount > 0) {
            distributions.add(ReportData.RiskDistribution.builder()
                    .riskType("模板同源")
                    .found(true)
                    .foundDescription("发现异常")
                    .needReview(true)
                    .needReviewText("建议复核")
                    .brief("文档结构和模板特征表现出同源特征")
                    .hitCount((int) templateCount)
                    .level("medium")
                    .build());
        }

        return distributions;
    }

    /**
     * 构建 V2 本次比对文件。
     */
    private List<ReportData.DocumentIndex> buildDocuments(TenderReviewData data) {
        List<ReportData.DocumentIndex> documents = new ArrayList<>();
        if (data == null || data.getDocuments() == null || data.getDocuments().isEmpty()) {
            return documents;
        }

        List<TenderDocument> docs = data.getDocuments();
        for (int i = 0; i < docs.size(); i++) {
            TenderDocument doc = docs.get(i);
            String partyName = inferPartyName(doc, i);
            String docName = firstNonBlank(doc.getDocumentName(), doc.getFilename(), "未命名标书");

            documents.add(ReportData.DocumentIndex.builder()
                    .docCode("文档 " + (char) ('A' + i))
                    .docId("DOC-" + String.format("%03d", i + 1))
                    .partyName(partyName)
                    .fileName(docName)
                    .docNature(i == 0 ? "审计主文档" : "关键参检文档")
                    .lastModifier("Admin_User")
                    .role((i == 0 ? "第一" : "第二") + "份投标文件参与比对")
                    .docRole("投标文件")
                    .id(doc.getDocumentId())
                    .build());
        }
        return documents;
    }

    /**
     * 构建 V2 关键证据明细（必须包含 A/B 双侧内容）。
     */
    private List<ReportData.EvidenceChain> buildEvidences(List<RuleHit> effectiveHits,
                                                         RiskFusionResult fusionResult,
                                                         EvidenceAssemblyResult evidenceAssemblyResult,
                                                         TenderReviewData data) {
        List<ReportData.EvidenceChain> evidences = new ArrayList<>();
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return evidences;
        }

        // 构建文档 A/B 名称映射
        Map<String, String> docIdToName = buildDocIdToName(data);
        List<TenderDocument> docs = data == null ? List.of() : data.getDocuments();
        String docAName = docs.size() > 0 ? docs.get(0).getDocumentName() : "文档 A";
        String docBName = docs.size() > 1 ? docs.get(1).getDocumentName() : "文档 B";

        int id = 1;
        for (RuleHit hit : effectiveHits.stream().limit(5).toList()) {
            String categoryKey = resolveGroupKeyForReport(hit);
            String evidenceType = resolveCategoryName(categoryKey);
            String level = normalizeRiskLevelCode(resolveItemRiskLevel(hit, fusionResult));

            // 提取 A/B 内容
            String docAContent = extractDocAContent(hit, docs);
            String docBContent = extractDocBContent(hit, docs);
            String ruleCode = safeText(hit.getRuleCode(), "UNKNOWN");

            evidences.add(ReportData.EvidenceChain.builder()
                    .evidenceId("E" + String.format("%02d", id++))
                    .type(evidenceType)
                    .level(level)
                    .evidenceChainId("RULE-" + ruleCode + "-" + String.format("%03d", id - 1))
                    .similarity(hit.getConfidence() != null ? String.format("%.1f%%", hit.getConfidence() * 100) : null)
                    .sourceType(evidenceType)
                    .docAName("文档 A（" + docAName + "）")
                    .docBName("文档 B（" + docBName + "）")
                    .docAContent(docAContent)
                    .docBContent(docBContent)
                    .comparisonFinding(buildComparisonFinding(hit, categoryKey))
                    .aiJudgment(buildAIJudgment(docAName, docBName, categoryKey, hit))
                    .reviewSuggestion(buildV2Action(categoryKey))
                    .title(hit.getRuleName())
                    .summary(hit.getTriggerSummary())
                    .build());
        }
        return evidences;
    }

    private String extractDocAContent(RuleHit hit, List<TenderDocument> docs) {
        if (hit.getEvidences() != null && !hit.getEvidences().isEmpty()) {
            for (RuleEvidence ev : hit.getEvidences()) {
                if (ev.getDocumentId() != null && docs.size() > 0 && ev.getDocumentId().equals(docs.get(0).getDocumentId())) {
                    return safeText(ev.getMatchedValue(), "（暂无对照内容）");
                }
            }
            // 如果没找到对应的，返回第一个证据的内容
            RuleEvidence first = hit.getEvidences().get(0);
            return safeText(first.getMatchedValue(), "（暂无对照内容）");
        }
        return safeText(hit.getTriggerSummary(), "（暂无对照内容）");
    }

    private String extractDocBContent(RuleHit hit, List<TenderDocument> docs) {
        if (hit.getEvidences() != null && !hit.getEvidences().isEmpty()) {
            // 尝试找第二个文档的内容
            for (RuleEvidence ev : hit.getEvidences()) {
                if (ev.getDocumentId() != null && docs.size() > 1 && ev.getDocumentId().equals(docs.get(1).getDocumentId())) {
                    return safeText(ev.getMatchedValue(), "（暂无对照内容）");
                }
            }
            // 如果只有一个证据或没找到匹配的，返回触发摘要
            if (hit.getEvidences().size() >= 2) {
                RuleEvidence second = hit.getEvidences().get(1);
                return safeText(second.getMatchedValue(), "（暂无对照内容）");
            }
        }
        return "（暂无对照内容）";
    }

    private String buildComparisonFinding(RuleHit hit, String categoryKey) {
        String summary = safeText(hit.getTriggerSummary(), "");
        if (!summary.isBlank()) {
            return summary;
        }
        return switch (categoryKey) {
            case "pricing" -> "报价项存在规律性差异，差异呈现固定模式。";
            case "team" -> "核心团队成员在不同投标文件中出现重复。";
            case "text_similarity" -> "关键条款在内容结构、句式和业务表达上高度一致。";
            case "template" -> "文档结构与模板特征高度接近。";
            default -> "发现需要复核的异常线索。";
        };
    }

    private String buildAIJudgment(String docAName, String docBName, String categoryKey, RuleHit hit) {
        String typeLabel = resolveCategoryName(categoryKey);
        String confidence = hit.getConfidence() != null ? String.format("%.0f", hit.getConfidence() * 100) : "Unknown";
        return "AI审查引擎经由\"确定性规约\"与\"LLM语义分析器\"交叉验证：【" + docAName + "】与【" + docBName + "】在\"" + typeLabel + "\"维度上表现出极高的协同特征，置信度达 " + confidence + "%，建议结合原文上下文和人工复核结论决定是否升级处理。";
    }

    /**
     * 构建 V2 处置建议（任务清单）。
     */
    private ReportData.ActionPlan buildActionPlan(RiskFusionResult fusionResult, List<RuleHit> effectiveHits) {
        List<ReportData.TaskItem> tasks = new ArrayList<>();

        // 按原型生成固定的5条建议
        tasks.add(ReportData.TaskItem.builder()
                .priority("high")
                .action("复核报价明细和报价形成依据")
                .role("评标专家 / 招采人员")
                .goal("判断报价异常是否成立")
                .build());
        tasks.add(ReportData.TaskItem.builder()
                .priority("high")
                .action("核查核心人员归属和授权关系")
                .role("风控 / 合规人员")
                .goal("判断团队重复是否合理")
                .build());
        tasks.add(ReportData.TaskItem.builder()
                .priority("medium")
                .action("对关键相似段落做人工原文比对")
                .role("评标专家")
                .goal("判断是否超出通用模板范围")
                .build());
        tasks.add(ReportData.TaskItem.builder()
                .priority("medium")
                .action("查询历史投标记录和历史模板")
                .role("招采管理员")
                .goal("判断是否存在长期协同或同源文件")
                .build());
        tasks.add(ReportData.TaskItem.builder()
                .priority("low")
                .action("归档系统报告、人工复核意见和佐证材料")
                .role("项目负责人")
                .goal("形成可追溯审查闭环")
                .build());

        return ReportData.ActionPlan.builder()
                .tasks(tasks)
                .level1Actions(List.of("复核报价明细和报价形成依据", "核查核心人员归属和授权关系"))
                .level2Actions(List.of("对关键相似段落做人工原文比对", "查询历史投标记录和历史模板"))
                .level3Actions(List.of("归档系统报告、人工复核意见和佐证材料"))
                .build();
    }

    /**
     * 构建 V2 报告元信息。
     */
    private ReportData.ReportMetadata buildMetadata(TenderReviewData data, RiskFusionResult fusionResult) {
        String riskLevel = fusionResult == null ? "UNKNOWN" : fusionResult.getRiskLevel();
        String docId = "TSR-" + java.time.LocalDate.now().toString().replace("-", "") + "-"
                + (fusionResult != null && fusionResult.getSummary() != null
                ? fusionResult.getSummary().hashCode() & 0xfff : "000");

        int docCount = data == null || data.getDocuments() == null ? 0 : data.getDocuments().size();

        return ReportData.ReportMetadata.builder()
                .generatedAt(java.time.OffsetDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .reviewScope(docCount + " 份文件")
                .documentId(docId)
                .taskId(docId)
                .reportId(docId)
                .projectTarget(data != null && data.getDocuments() != null && !data.getDocuments().isEmpty()
                        ? data.getDocuments().stream().map(TenderDocument::getDocumentName).collect(Collectors.joining("、"))
                        : "-")
                .reviewType(docCount > 2 ? "多文件比对" : "双文件比对")
                .systemVersion("drug-agent-v1.0.0")
                .build();
    }

    private String resolveRiskLevelLabel(String levelCode) {
        return switch (levelCode) {
            case "high" -> "重大围标风险";
            case "medium" -> "中度围标风险";
            case "low" -> "轻度异常信号";
            default -> "暂未发现明显异常";
        };
    }

    private Page1Summary buildPage1Summary(TenderReviewData data,
                                           List<RuleHit> rawHits,
                                           List<RuleHit> effectiveHits,
                                           List<ExemptionHit> exemptionHits,
                                           RiskFusionResult fusionResult) {
        List<TenderDocument> documents = data == null ? List.of() : data.getDocuments();
        String riskLevelCode = normalizeRiskLevelCode(fusionResult == null ? null : fusionResult.getRiskLevel());
        Integer score = fusionResult == null ? 0 : fusionResult.getScore();

        return Page1Summary.builder()
                .riskLevel(riskLevelCode)
                .conclusion(buildConclusion(fusionResult, effectiveHits, exemptionHits))
                .recommendedAction(buildDecisionAction(effectiveHits, riskLevelCode))
                .riskScore(score)
                .coreEvidenceCount(effectiveHits == null ? 0 : Math.min(effectiveHits.size(), 5))
                .ruleHitCount(rawHits == null ? 0 : rawHits.size())
                .coreRiskTop3(buildCoreRiskTop3(effectiveHits, fusionResult))
                .riskDistribution(buildRiskDistribution(effectiveHits))
                .documents(buildDocumentInfos(documents))
                .build();
    }

    private List<Page1Summary.CoreRisk> buildCoreRiskTop3(List<RuleHit> effectiveHits, RiskFusionResult fusionResult) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return List.of();
        }

        List<RuleHit> sortedHits = new ArrayList<>(effectiveHits);
        sortedHits.sort(Comparator.comparing(this::effectiveWeight, Comparator.nullsLast(Comparator.reverseOrder())));

        List<Page1Summary.CoreRisk> coreRisks = new ArrayList<>();
        int rank = 1;
        for (RuleHit hit : sortedHits.stream().limit(3).toList()) {
            String categoryKey = resolveGroupKeyForReport(hit);
            coreRisks.add(Page1Summary.CoreRisk.builder()
                    .rank(rank++)
                    .riskType(categoryKey)
                    .title(buildCoreRiskTitle(hit))
                    .level(normalizeRiskLevelCode(resolveItemRiskLevel(hit, fusionResult)))
                    .summary(buildTopRiskSummary(hit))
                    .action(buildCategoryAction(categoryKey))
                    .build());
        }
        return coreRisks;
    }

    private Map<String, String> buildRiskDistribution(List<RuleHit> effectiveHits) {
        Map<String, String> distribution = new LinkedHashMap<>();
        distribution.put("报价风险", resolveCategoryLevelCode("pricing", effectiveHits));
        distribution.put("团队风险", resolveCategoryLevelCode("team", effectiveHits));
        distribution.put("文本相似风险", resolveCategoryLevelCode("text_similarity", effectiveHits));
        distribution.put("模板同源风险", resolveCategoryLevelCode("template", effectiveHits));
        distribution.put("其他辅助风险", resolveCategoryLevelCode("auxiliary", effectiveHits));
        return distribution;
    }

    private String resolveCategoryLevelCode(String categoryKey, List<RuleHit> effectiveHits) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return "safe";
        }
        int maxWeight = effectiveHits.stream()
                .filter(hit -> categoryKey.equals(resolveGroupKeyForReport(hit)))
                .map(this::effectiveWeight)
                .filter(weight -> weight != null)
                .max(Integer::compareTo)
                .orElse(0);
        if (maxWeight >= 85) {
            return "high";
        }
        if (maxWeight >= 60) {
            return "medium";
        }
        if (maxWeight > 0) {
            return "low";
        }
        return "safe";
    }

    private List<Page1Summary.DocumentInfo> buildDocumentInfos(List<TenderDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<Page1Summary.DocumentInfo> infos = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            TenderDocument doc = documents.get(i);
            infos.add(Page1Summary.DocumentInfo.builder()
                    .docId(doc.getDocumentId())
                    .docName(firstNonBlank(doc.getDocumentName(), doc.getFilename(), "未命名标书"))
                    .party(inferPartyName(doc, i))
                    .role("对比文档 " + (i + 1))
                    .internalId(firstNonBlank(doc.getDocumentId(), "UPLOAD-" + i))
                    .build());
        }
        return infos;
    }

    private String inferPartyName(TenderDocument doc, int index) {
        String raw = firstNonBlank(doc == null ? null : doc.getDocumentName(),
                doc == null ? null : doc.getFilename(), "");
        if (!raw.isBlank()) {
            String normalized = raw.replaceAll("\\.(pdf|doc|docx|txt)$", "");
            String[] parts = normalized.split("[_－-]");
            for (String part : parts) {
                String candidate = part == null ? "" : part.trim();
                if (candidate.isBlank()) {
                    continue;
                }
                if (candidate.startsWith("投标人") || candidate.startsWith("标书") || candidate.startsWith("测试")) {
                    continue;
                }
                if (candidate.contains("测试标书")) {
                    candidate = candidate.replace("测试标书", "").trim();
                }
                if (!candidate.isBlank() && candidate.length() <= 24) {
                    return candidate;
                }
            }
        }
        return "投标方" + (char) ('A' + index);
    }

    private String buildConclusion(RiskFusionResult fusionResult, List<RuleHit> effectiveHits, List<ExemptionHit> exemptionHits) {
        int hitCount = effectiveHits == null ? 0 : effectiveHits.size();
        int exemptionCount = exemptionHits == null ? 0 : exemptionHits.size();
        String riskLevelCode = normalizeRiskLevelCode(fusionResult == null ? null : fusionResult.getRiskLevel());

        if (hitCount == 0) {
            return "本次比对未发现需要升级处理的高风险线索，当前材料在报价、团队与文本相似度等维度未见明显异常，建议保留结果并进行抽样复核。";
        }

        String leadingRisks = buildLeadingRiskLabels(effectiveHits);
        StringBuilder builder = new StringBuilder();
        builder.append("本次比对发现")
                .append(leadingRisks)
                .append("等高置信风险信号，综合判定为")
                .append(toRiskLevelLabel(riskLevelCode))
                .append("，")
                .append(buildDecisionAction(effectiveHits, riskLevelCode))
                .append("。");
        if (exemptionCount > 0) {
            builder.append("另有").append(exemptionCount).append("项线索经豁免或降权处理，可在附录中追溯。");
        }
        return builder.toString();
    }

    private String buildLeadingRiskLabels(List<RuleHit> effectiveHits) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return "多项异常线索";
        }
        return effectiveHits.stream()
                .sorted(Comparator.comparing(this::effectiveWeight, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::resolveGroupKeyForReport)
                .map(this::resolveCategoryName)
                .distinct()
                .limit(3)
                .collect(Collectors.joining("、"));
    }

    private String buildDecisionAction(List<RuleHit> effectiveHits, String riskLevelCode) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return "建议保留本次审查结果，并对关键章节进行抽样复核";
        }
        return switch (riskLevelCode) {
            case "high" -> "建议立即启动人工复核，并优先核查投标主体关联关系、核心人员归属及报价形成依据";
            case "medium" -> "建议尽快开展人工核验，补充核查关键条款、联系人信息和历史投标记录";
            default -> "建议纳入持续观察，必要时补充人工抽检";
        };
    }

    private Page2RiskOverview buildPage2RiskOverview(List<RuleHit> effectiveHits, RiskFusionResult fusionResult) {
        List<Page2RiskOverview.RiskCategory> categories = List.of(
                buildRiskCategory("pricing", "报价风险", effectiveHits, fusionResult),
                buildRiskCategory("team", "团队风险", effectiveHits, fusionResult),
                buildRiskCategory("text_similarity", "文本相似风险", effectiveHits, fusionResult),
                buildRiskCategory("template", "模板同源风险", effectiveHits, fusionResult),
                buildRiskCategory("auxiliary", "其他辅助风险", effectiveHits, fusionResult)
        );

        return Page2RiskOverview.builder()
                .riskCategories(categories)
                .build();
    }

    private Page2RiskOverview.RiskCategory buildRiskCategory(String type,
                                                             String categoryName,
                                                             List<RuleHit> effectiveHits,
                                                             RiskFusionResult fusionResult) {
        List<RuleHit> typeHits = effectiveHits == null
                ? List.of()
                : effectiveHits.stream()
                .filter(hit -> type.equals(resolveGroupKeyForReport(hit)))
                .sorted(Comparator.comparing(this::effectiveWeight, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        RuleHit topHit = typeHits.isEmpty() ? null : typeHits.get(0);
        String level = resolveCategoryLevelCode(type, effectiveHits);
        boolean needHumanReview = !typeHits.isEmpty();

        return Page2RiskOverview.RiskCategory.builder()
                .type(type)
                .categoryName(categoryName)
                .level(level)
                .hitCount(typeHits.size())
                .needHumanReview(needHumanReview)
                .explanation(buildCategoryExplanation(type, typeHits.size(), topHit, fusionResult))
                .representativeEvidence(buildRepresentativeEvidence(topHit))
                .action(buildCategoryAction(type))
                .build();
    }

    private String resolveGroupKeyForReport(RuleHit hit) {
        if (hit == null || hit.getRuleCode() == null) {
            return "auxiliary";
        }
        String ruleCode = hit.getRuleCode();
        if (ruleCode.startsWith("W-M1")) {
            return "pricing";
        }
        if (ruleCode.startsWith("W-M2") || ruleCode.startsWith("W-M3")) {
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

    private Page3CoreEvidence buildPage3CoreEvidence(List<RuleHit> effectiveHits, RiskFusionResult fusionResult) {
        if (effectiveHits == null || effectiveHits.isEmpty()) {
            return Page3CoreEvidence.builder()
                    .evidenceList(List.of())
                    .build();
        }

        List<RuleHit> sortedHits = new ArrayList<>(effectiveHits);
        sortedHits.sort(Comparator.comparing(this::effectiveWeight, Comparator.nullsLast(Comparator.reverseOrder())));

        List<Page3CoreEvidence.Evidence> evidences = new ArrayList<>();
        int id = 1;
        for (RuleHit hit : sortedHits.stream().limit(5).toList()) {
            Map<String, Object> keyFindings = new LinkedHashMap<>();
            keyFindings.put("检测项目", resolveDisplayLabel(fallback(hit.getRuleCode(), "-")));
            keyFindings.put("风险类型", resolveCategoryName(resolveGroupKeyForReport(hit)));
            keyFindings.put("涉及文档数", hit.getDocumentIds() == null ? 0 : hit.getDocumentIds().size());
            if (effectiveWeight(hit) != null) {
                keyFindings.put("风险关注度", riskAttentionLabel(effectiveWeight(hit)));
            }
            if (hit.getConfidence() != null) {
                keyFindings.put("判定可信度", hit.getConfidence() >= 0.8 ? "高" : hit.getConfidence() >= 0.5 ? "中" : "低");
            }
            if (hit.getEvidences() != null && !hit.getEvidences().isEmpty()) {
                keyFindings.put("定位章节", fallback(hit.getEvidences().get(0).getChapterPath(), "原文片段"));
            }

            evidences.add(Page3CoreEvidence.Evidence.builder()
                    .id(String.format("E%02d", id++))
                    .type(resolveCategoryName(resolveGroupKeyForReport(hit)))
                    .level(normalizeRiskLevelCode(resolveItemRiskLevel(hit, fusionResult)))
                    .confidence(resolveConfidence(hit))
                    .title(buildCoreRiskTitle(hit))
                    .explanation(buildEvidenceExplanation(hit))
                    .keyFindings(keyFindings)
                    .basis(buildEvidenceBasis(hit, fusionResult))
                    .action(buildCategoryAction(resolveGroupKeyForReport(hit)))
                    .build());
        }

        return Page3CoreEvidence.builder()
                .evidenceList(evidences)
                .build();
    }

    private Page4DetailComparison buildPage4DetailComparison(TenderReviewData data, List<RuleHit> effectiveHits) {
        return evidenceAssembler.buildDetailComparison(effectiveHits, buildDocIdToName(data));
    }

    private Page5ActionSuggestions buildPage5ActionSuggestions(RiskFusionResult fusionResult,
                                                              List<RuleHit> effectiveHits,
                                                              List<ExemptionHit> exemptionHits) {
        String focus = buildLeadingRiskLabels(effectiveHits);

        Page5ActionSuggestions.ActionLevel level1 = Page5ActionSuggestions.ActionLevel.builder()
                .title("一级动作｜立即执行")
                .objective("快速判断是否需要升级处理")
                .actions(List.of(
                        Page5ActionSuggestions.Action.builder()
                                .action("人工复核核心证据，优先确认" + focus + "是否构成实质性异常。")
                                .role("评标专家")
                                .priority("高")
                                .build(),
                        Page5ActionSuggestions.Action.builder()
                                .action("复核两份投标文件是否由独立团队编制，重点核查关键章节、报价明细和团队信息的独立性。")
                                .role("评标专家")
                                .priority("高")
                                .build(),
                        Page5ActionSuggestions.Action.builder()
                                .action("结合招采规则判断关键线索是否达到废标、预警上报或专项核查条件。")
                                .role("风控专员")
                                .priority("高")
                                .build()
                ))
                .build();

        Page5ActionSuggestions.ActionLevel level2 = Page5ActionSuggestions.ActionLevel.builder()
                .title("二级动作｜进一步核验")
                .objective("补强证据链")
                .actions(List.of(
                        Page5ActionSuggestions.Action.builder()
                                .action("核查投标主体工商关联关系、授权链路和联系人网络，排查是否存在隐性关联。")
                                .role("风控专员")
                                .priority("高")
                                .build(),
                        Page5ActionSuggestions.Action.builder()
                                .action("核查核心人员社保归属、劳动关系、任职证明及项目履历，确认是否存在人员复用。")
                                .role("风控专员")
                                .priority("高")
                                .build(),
                        Page5ActionSuggestions.Action.builder()
                                .action("核对邮箱、联系电话、地址、附件模板及历史联系方式，补充外围佐证材料。")
                                .role("招采管理员")
                                .priority("中")
                                .build()
                ))
                .build();

        Page5ActionSuggestions.ActionLevel level3 = Page5ActionSuggestions.ActionLevel.builder()
                .title("三级动作｜必要时追溯")
                .objective("形成完整判断依据")
                .actions(List.of(
                        Page5ActionSuggestions.Action.builder()
                                .action("调取历史投标记录，查看是否存在同源模板、重复团队或长期协同报价模式。")
                                .role("招采管理员")
                                .priority("中")
                                .build(),
                        Page5ActionSuggestions.Action.builder()
                                .action("复核历史报价曲线与过往异常样本，判断是否存在持续性规律异常。")
                                .role("风控专员")
                                .priority("中")
                                .build(),
                        Page5ActionSuggestions.Action.builder()
                                .action("必要时纳入专项审查、合规留档或监管协同处理流程。")
                                .role("合规负责人")
                                .priority("中")
                                .build()
                ))
                .build();

        return Page5ActionSuggestions.builder()
                .level1(level1)
                .level2(level2)
                .level3(level3)
                .retentionAdvice(List.of(
                        "保留本次审查报告、命中规则清单与原始证据片段，形成完整留痕。",
                        "记录人工复核结论、复核人、复核时间及处理动作，避免后续口径不一致。",
                        "对升级处理事项同步保存关联企业核验材料、历史投标记录及外部佐证。"))
                .build();
    }

    /**
     * 根据规则名称或规则编码返回可读标签。
     */
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

    private Page6Appendix buildPage6Appendix(List<RuleHit> rawHits, RiskFusionResult fusionResult) {
        // 构建规则列表
        List<Page6Appendix.RuleInfo> ruleInfos = new ArrayList<>();
        if (rawHits != null) {
            Set<String> ruleCodes = rawHits.stream()
                    .map(RuleHit::getRuleCode)
                    .filter(c -> c != null)
                    .collect(Collectors.toSet());
            int idx = 1;
            for (String code : ruleCodes) {
                ruleInfos.add(Page6Appendix.RuleInfo.builder()
                        .ruleId("R" + String.format("%03d", idx++))
                        .ruleCode(code)
                        .description(resolveDisplayLabel(code))
                        .build());
            }
        }

        // 构建证据片段（取前3条命中的典型证据）
        List<Page6Appendix.EvidenceFragment> fragments = new ArrayList<>();
        if (rawHits != null && !rawHits.isEmpty()) {
            int idx = 1;
            for (RuleHit hit : rawHits.stream().limit(3).toList()) {
                if (hit.getEvidences() != null && !hit.getEvidences().isEmpty()) {
                    for (var ev : hit.getEvidences().stream().limit(2).toList()) {
                        fragments.add(Page6Appendix.EvidenceFragment.builder()
                                .fragmentId("F" + String.format("%02d", idx++))
                                .content(fallback(ev.getMatchedValue(), "-"))
                                .source(fallback(ev.getChapterPath(), "-"))
                                .build());
                    }
                }
            }
        }

        // 任务信息
        Page6Appendix.TaskInfo taskInfo = Page6Appendix.TaskInfo.builder()
                .taskId("-")
                .reviewTime(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .modelVersion("drug-agent-v1.0.0")
                .build();

        return Page6Appendix.builder()
                .ruleList(ruleInfos)
                .evidenceFragments(fragments)
                .taskInfo(taskInfo)
                .build();
    }
}
