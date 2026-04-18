package com.liang.drugagent.scene.tender_review.semantic.analyzer;

import com.liang.drugagent.scene.tender_review.model.RuleEvidence;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.model.semantic.TenderSemanticJudgeReq;
import com.liang.drugagent.scene.tender_review.model.semantic.TenderSemanticJudgeResp;
import com.liang.drugagent.scene.tender_review.service.TenderSemanticCandidateService;
import com.liang.drugagent.scene.tender_review.service.TenderSemanticReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * W-M8 商务条款配合语义分析器。
 *
 * <p>职责：判断两份标书的商务条款响应是否存在互补配合或策略协同关系。
 *
 * <p>判断标准：
 * <ul>
 *   <li>一方完全接受、另一方附条件接受是否构成互补配合</li>
 *   <li>表面差异背后是否存在策略协同</li>
 *   <li>是否存在"一个强响应、一个柔性偏离"的配合模式</li>
 * </ul>
 *
 * <p>不命中典型情况：
 * <ul>
 *   <li>双方均完全接受招标文件要求</li>
 *   <li>差异源于各自独立的商业判断（如不同的付款周期偏好）</li>
 *   <li>差异仅为行业惯例性表述</li>
 * </ul>
 *
 * <p>注意：本分析器输出的 RuleHit 的 hitSource 为 "LLM_SEMANTIC_RULE"（标注在 triggerSummary 中），
 * 用于与确定性规则命中的 RuleHit 区分。
 *
 * @author liangjiajian
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommercialCoordinationSemanticAnalyzer implements TenderSemanticAnalyzer {

    private static final String ANALYZER_CODE = "W-M8";
    private static final String COMPARE_TOPIC = "商务条款";
    private static final String RISK_TYPE = "collusion";
    private static final String RULE_NAME = "商务条款配合分析";
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.85;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.60;

    private final TenderSemanticCandidateService candidateService;
    private final TenderSemanticReviewService reviewService;

    @Override
    public String analyzerCode() {
        return ANALYZER_CODE;
    }

    @Override
    public List<RuleHit> analyze(TenderReviewData data) {
        // 1. 获取参与审查的文档对
        List<String[]> documentPairs = extractDocumentPairs(data);
        if (documentPairs.isEmpty()) {
            log.info("[CommercialCoordinationSemanticAnalyzer] 未找到有效的文档对，跳过 W-M8 分析");
            return List.of();
        }

        List<RuleHit> allHits = new ArrayList<>();

        // 2. 对每对文档进行语义判断
        for (String[] pair : documentPairs) {
            String leftDocId = pair[0];
            String rightDocId = pair[1];

            // 3. 召回候选片段对
            List<TenderSemanticJudgeReq> candidates = candidateService.recallCandidates(
                    data,
                    ANALYZER_CODE,
                    COMPARE_TOPIC,
                    leftDocId,
                    rightDocId
            );

            if (candidates.isEmpty()) {
                log.info("[CommercialCoordinationSemanticAnalyzer] 文档对 {} - {} 无候选片段，跳过", leftDocId, rightDocId);
                continue;
            }

            log.info("[CommercialCoordinationSemanticAnalyzer] 文档对 {} - {} 召回 {} 组候选片段，开始 LLM 语义判断",
                    leftDocId, rightDocId, candidates.size());

            // 4. 对每组候选片段发起 LLM 裁决
            for (TenderSemanticJudgeReq candidate : candidates) {
                try {
                    TenderSemanticJudgeResp resp = reviewService.judge(candidate);

                    // 5. 将 LLM 响应转换为 RuleHit
                    RuleHit hit = convertToRuleHit(resp, data, leftDocId, rightDocId);
                    if (hit != null) {
                        allHits.add(hit);
                        log.info("[CommercialCoordinationSemanticAnalyzer] 文档对 {} - {} LLM 判断命中 - confidence: {}, hit: {}",
                                leftDocId, rightDocId, resp.getConfidence(), resp.getHit());
                    }
                } catch (Exception e) {
                    log.error("[CommercialCoordinationSemanticAnalyzer] LLM 裁决异常 - leftDoc: {}, rightDoc: {}, error: {}",
                            leftDocId, rightDocId, e.getMessage());
                }
            }
        }

        return allHits;
    }

    /**
     * 从审查数据中提取文档对列表。
     */
    private List<String[]> extractDocumentPairs(TenderReviewData data) {
        List<TenderDocument> documents = data.getDocuments();
        if (documents == null || documents.size() < 2) {
            return List.of();
        }

        List<String[]> pairs = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            for (int j = i + 1; j < documents.size(); j++) {
                pairs.add(new String[]{documents.get(i).getDocumentId(), documents.get(j).getDocumentId()});
            }
        }
        return pairs;
    }

    /**
     * 将 LLM 裁决响应转换为 RuleHit。
     *
     * <p>注意：置信度低于 0.60 的结果不直接触发高风险，仅作为辅助参考。
     * RuleHit 的来源通过 triggerSummary 中的 [LLM_SEMANTIC_RULE] 标记区分。
     */
    private RuleHit convertToRuleHit(TenderSemanticJudgeResp resp, TenderReviewData data,
                                      String leftDocId, String rightDocId) {
        if (resp.getConfidence() == null || resp.getConfidence() < MEDIUM_CONFIDENCE_THRESHOLD) {
            log.info("[CommercialCoordinationSemanticAnalyzer] LLM 置信度 {} 低于阈值 {}，不直接触发规则命中，仅作辅助参考",
                    resp.getConfidence(), MEDIUM_CONFIDENCE_THRESHOLD);
            return RuleHit.builder()
                    .hitId(UUID.randomUUID().toString())
                    .ruleCode(ANALYZER_CODE)
                    .ruleName(RULE_NAME)
                    .riskType(RISK_TYPE)
                    .priority("LOW")
                    .weight(0)
                    .confidence(resp.getConfidence())
                    .triggerSummary(String.format("[LLM_SEMANTIC_RULE] W-M8 商务条款疑似配合（置信度%.2f，建议人工复核）", resp.getConfidence()))
                    .documentIds(List.of(leftDocId, rightDocId))
                    .evidences(buildEvidences(resp, data))
                    .build();
        }

        String priority = resp.getConfidence() >= HIGH_CONFIDENCE_THRESHOLD ? "HIGH" : "MEDIUM";
        int weight = calculateWeight(resp.getConfidence());

        return RuleHit.builder()
                .hitId(UUID.randomUUID().toString())
                .ruleCode(ANALYZER_CODE)
                .ruleName(RULE_NAME)
                .riskType(RISK_TYPE)
                .priority(priority)
                .weight(weight)
                .confidence(resp.getConfidence())
                .triggerSummary(String.format("[LLM_SEMANTIC_RULE] W-M8 商务条款配合 - %s", resp.getConclusion()))
                .matchedValue(resp.getConclusion())
                .documentIds(List.of(leftDocId, rightDocId))
                .evidences(buildEvidences(resp, data))
                .build();
    }

    /**
     * 根据置信度计算权重。
     */
    private int calculateWeight(Double confidence) {
        if (confidence == null) {
            return 0;
        }
        if (confidence >= 0.90) {
            return 80;
        } else if (confidence >= HIGH_CONFIDENCE_THRESHOLD) {
            return 60;
        } else {
            return 40;
        }
    }

    /**
     * 构建证据列表。
     */
    private List<RuleEvidence> buildEvidences(TenderSemanticJudgeResp resp, TenderReviewData data) {
        if (resp.getEvidences() == null) {
            return List.of();
        }

        return resp.getEvidences().stream()
                .map(evidence -> RuleEvidence.builder()
                        .documentId(evidence.getDocumentId())
                        .chapterPath(evidence.getChapterPath())
                        .matchedValue(evidence.getExcerpt())
                        .originalValue(evidence.getExcerpt())
                        .build())
                .toList();
    }
}
