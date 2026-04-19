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
 * W-M6 商务条款语义分析器。
 *
 * <p>职责：判断两份标书的商务条款章节是否存在语义层面的同源抄袭。
 *
 * <p>判断标准：
 * <ul>
 *   <li>关键技术响应内容高度雷同</li>
 *   <li>故意缺项或无效资质模式</li>
 *   <li>付款条件、违约责任等条款表述一致</li>
 * </ul>
 *
 * <p>不命中典型情况：
 * <ul>
 *   <li>仅引用行业通用商务条款模板</li>
 *   <li>仅遵循招标文件要求的标准化商务应答</li>
 *   <li>商务条款存在实质性差异，具有独立谈判痕迹</li>
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
public class CommercialTermsAnalyzer implements TenderSemanticAnalyzer {

    private static final String ANALYZER_CODE = "W-M6";
    private static final String COMPARE_TOPIC = "商务条款";
    private static final String RISK_TYPE = "collusion";
    private static final String RULE_NAME = "商务条款雷同";
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.85;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.70;

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
            log.info("[CommercialTermsAnalyzer] 未找到有效的文档对，跳过 W-M6 分析");
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
                log.info("[CommercialTermsAnalyzer] 文档对 {} - {} 无候选片段，跳过", leftDocId, rightDocId);
                continue;
            }

            log.info("[CommercialTermsAnalyzer] 文档对 {} - {} 召回 {} 组候选片段，开始 LLM 语义判断",
                    leftDocId, rightDocId, candidates.size());

            // 4. 对每组候选片段发起 LLM 裁决
            for (TenderSemanticJudgeReq candidate : candidates) {
                try {
                    TenderSemanticJudgeResp resp = reviewService.judge(candidate);

                    // 5. 将 LLM 响应转换为 RuleHit
                    RuleHit hit = convertToRuleHit(resp, data, leftDocId, rightDocId);
                    if (hit != null) {
                        allHits.add(hit);
                        log.info("[CommercialTermsAnalyzer] 文档对 {} - {} LLM 判断命中 - confidence: {}, hit: {}",
                                leftDocId, rightDocId, resp.getConfidence(), resp.getHit());
                    }
                } catch (Exception e) {
                    log.error("[CommercialTermsAnalyzer] LLM 裁决异常 - leftDoc: {}, rightDoc: {}, error: {}",
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
     */
    private RuleHit convertToRuleHit(TenderSemanticJudgeResp resp, TenderReviewData data,
                                      String leftDocId, String rightDocId) {
        if (resp.getConfidence() == null || resp.getConfidence() < MEDIUM_CONFIDENCE_THRESHOLD) {
            log.info("[CommercialTermsAnalyzer] LLM 置信度 {} 低于阈值 {}，不直接触发规则命中，仅作辅助参考",
                    resp.getConfidence(), MEDIUM_CONFIDENCE_THRESHOLD);
            return RuleHit.builder()
                    .hitId(UUID.randomUUID().toString())
                    .ruleCode(ANALYZER_CODE)
                    .ruleName(RULE_NAME)
                    .riskType(RISK_TYPE)
                    .priority("LOW")
                    .weight(0)
                    .confidence(resp.getConfidence())
                    .triggerSummary(String.format("[LLM_SEMANTIC_RULE] W-M6 商务条款疑似同源（置信度%.2f，建议人工复核）", resp.getConfidence()))
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
                .triggerSummary(String.format("[LLM_SEMANTIC_RULE] W-M6 商务条款语义同源 - %s", resp.getConclusion()))
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