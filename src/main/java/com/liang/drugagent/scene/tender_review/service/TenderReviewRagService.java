package com.liang.drugagent.scene.tender_review.service;

import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewRagEvidence;
import com.liang.drugagent.shared.model.EvidenceGroup;
import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.RagOutcome;
import com.liang.drugagent.shared.tool.KnowledgeRetrievalTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标书审查 RAG 适配服务。
 *
 * <p>职责边界：
 * <ul>
 *   <li>根据标书审查命中的风险类型构造法规检索问题</li>
 *   <li>通过 KnowledgeRetrievalTool 获取法规/标准/案例证据</li>
 *   <li>将 RagOutcome 转换为标书审查可消费的 EvidenceGroup</li>
 * </ul>
 *
 * <p>本服务不负责风险评分，不覆盖规则命中结论。</p>
 */
@Slf4j
@Service
public class TenderReviewRagService {

    private static final String SCENE = "tender_review";
    private static final String DOC_TYPE_REGULATION = "REGULATION";
    private static final int TOP_K_PER_QUERY = 3;
    private static final int MAX_QUERY_COUNT = 4;

    private final KnowledgeRetrievalTool knowledgeRetrievalTool;

    public TenderReviewRagService(KnowledgeRetrievalTool knowledgeRetrievalTool) {
        this.knowledgeRetrievalTool = knowledgeRetrievalTool;
    }

    /**
     * 根据有效风险命中补充法规证据。
     */
    public TenderReviewRagEvidence retrieveEvidence(List<RuleHit> effectiveHits, String orgId, String traceId) {
        if (orgId == null || orgId.isBlank()) {
            log.info("[TenderReviewRagService] orgId 为空，跳过标书审查 RAG 检索 - traceId={}", traceId);
            return TenderReviewRagEvidence.skipped("MISSING_ORG_ID");
        }

        if (effectiveHits == null || effectiveHits.isEmpty()) {
            log.info("[TenderReviewRagService] 无有效风险命中，跳过标书审查 RAG 检索 - traceId={}", traceId);
            return TenderReviewRagEvidence.skipped("NO_EFFECTIVE_HIT");
        }

        List<RagQuerySpec> querySpecs = buildQuerySpecs(effectiveHits);
        if (querySpecs.isEmpty()) {
            return TenderReviewRagEvidence.skipped("NO_RAG_QUERY");
        }

        List<EvidenceItem> items = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        int executedQueries = 0;

        for (RagQuerySpec spec : querySpecs.stream().limit(MAX_QUERY_COUNT).toList()) {
            executedQueries++;
            try {
                RagOutcome outcome = knowledgeRetrievalTool.search(
                        spec.question(),
                        orgId,
                        SCENE,
                        DOC_TYPE_REGULATION,
                        TOP_K_PER_QUERY
                );
                if (outcome == null) {
                    reasons.add(spec.riskType() + ":NULL_OUTCOME");
                    continue;
                }
                if (outcome.getReason() != null && !outcome.getReason().isBlank()) {
                    reasons.add(spec.riskType() + ":" + outcome.getReason());
                }
                List<EvidenceItem> evidenceList = outcome.getEvidenceList() == null
                        ? List.of()
                        : outcome.getEvidenceList();
                for (EvidenceItem item : evidenceList) {
                    if (item == null || item.getContent() == null || item.getContent().isBlank()) {
                        continue;
                    }
                    items.add(EvidenceItem.builder()
                            .title(buildEvidenceTitle(spec, item))
                            .content(buildEvidenceContent(spec, item))
                            .source(item.getSource() == null ? "rag" : item.getSource())
                            .ragChunkId(item.getRagChunkId())
                            .ragSourceId(item.getRagSourceId())
                            .ragScore(item.getRagScore())
                            .build());
                }
            } catch (Exception e) {
                log.warn("[TenderReviewRagService] RAG 检索异常，已降级 - traceId={}, riskType={}, error={}",
                        traceId, spec.riskType(), e.getMessage());
                reasons.add(spec.riskType() + ":DEGRADED");
            }
        }

        if (items.isEmpty()) {
            log.info("[TenderReviewRagService] 标书审查 RAG 未命中法规证据 - traceId={}, queries={}, reasons={}",
                    traceId, executedQueries, reasons);
            return TenderReviewRagEvidence.noHit(String.join(",", reasons));
        }

        EvidenceGroup group = EvidenceGroup.builder()
                .groupKey("rag_legal_basis")
                .title("法规与审查依据")
                .source("rag")
                .summary("基于有效风险命中补充法规/审查标准依据，共 " + items.size() + " 条引用。")
                .items(items)
                .build();

        log.info("[TenderReviewRagService] 标书审查 RAG 命中法规证据 - traceId={}, queries={}, hitCount={}",
                traceId, executedQueries, items.size());
        return TenderReviewRagEvidence.builder()
                .status("SUPPORTED")
                .reason(String.join(",", reasons))
                .hitCount(items.size())
                .items(items)
                .group(group)
                .build();
    }

    private List<RagQuerySpec> buildQuerySpecs(List<RuleHit> hits) {
        Map<String, RagQuerySpec> specs = new LinkedHashMap<>();
        for (RuleHit hit : hits) {
            String riskType = resolveRiskType(hit);
            RagQuerySpec spec = querySpecFor(riskType);
            if (spec != null) {
                specs.putIfAbsent(riskType, spec);
            }
        }
        return new ArrayList<>(specs.values());
    }

    private String resolveRiskType(RuleHit hit) {
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
        if (ruleCode.startsWith("W-M4")) {
            return "template";
        }
        if (ruleCode.startsWith("W-P")) {
            return "similarity";
        }
        return "collusion";
    }

    private RagQuerySpec querySpecFor(String riskType) {
        return switch (riskType) {
            case "pricing" -> new RagQuerySpec(
                    "pricing",
                    "报价异常接近、固定价差或报价梯度异常在招投标审查中如何判断串通投标风险？相关法规依据是什么？",
                    "报价异常依据"
            );
            case "contact" -> new RagQuerySpec(
                    "contact",
                    "不同投标人的联系人、电话、地址、邮箱等联系方式存在关联时，招投标法规如何认定串通投标风险？",
                    "联系人关联依据"
            );
            case "team" -> new RagQuerySpec(
                    "team",
                    "多个投标人的项目团队、核心人员或服务班底高度重叠时，招投标审查如何判断围标串标风险？",
                    "团队重叠依据"
            );
            case "template" -> new RagQuerySpec(
                    "template",
                    "不同投标文件目录结构、版式模板高度一致是否可能构成串通投标线索？相关审查标准是什么？",
                    "模板同源依据"
            );
            case "similarity" -> new RagQuerySpec(
                    "similarity",
                    "不同投标文件技术方案、实施方法、服务承诺或风险识别内容高度相似时，如何判断围标或串通投标风险？",
                    "内容雷同依据"
            );
            default -> new RagQuerySpec(
                    "collusion",
                    "医药招投标场景中围标、串标、串通投标的常见认定标准和法规依据是什么？",
                    "围串标通用依据"
            );
        };
    }

    private String buildEvidenceTitle(RagQuerySpec spec, EvidenceItem item) {
        String sourceTitle = item.getTitle() == null || item.getTitle().isBlank() ? "知识库引用" : item.getTitle();
        return spec.titlePrefix() + "：" + sourceTitle;
    }

    private String buildEvidenceContent(RagQuerySpec spec, EvidenceItem item) {
        return "风险类型=" + spec.riskType() + "。检索问题：" + spec.question()
                + "。引用片段：" + item.getContent();
    }

    private record RagQuerySpec(String riskType, String question, String titlePrefix) {
    }
}
