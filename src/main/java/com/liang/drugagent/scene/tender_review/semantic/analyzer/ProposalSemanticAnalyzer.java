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
 * W-P1 技术方案抄袭语义分析器。
 *
 * <p>职责：判断两份标书的技术方案章节是否属于实质同源改写。
 *
 * <p>判断标准：
 * <ul>
 *   <li>是否共享相同的系统架构骨架</li>
 *   <li>是否共享相同的模块划分和业务闭环逻辑</li>
 *   <li>是否明显缺乏独立编写痕迹</li>
 * </ul>
 *
 * <p>不命中典型情况：
 * <ul>
 *   <li>仅出现行业通用术语（如"高可用"、"负载均衡"）</li>
 *   <li>仅出现法规引用、招标文件要求复述</li>
 *   <li>模板化章节标题相同但内容独立编写</li>
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
public class ProposalSemanticAnalyzer implements TenderSemanticAnalyzer {

    private static final String ANALYZER_CODE = "W-P1";
    private static final String COMPARE_TOPIC = "技术方案";
    private static final String RISK_TYPE = "collusion";
    private static final String RULE_NAME = "技术方案语义抄袭";
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
            log.info("[ProposalSemanticAnalyzer] 未找到有效的文档对，跳过 W-P1 分析");
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
                log.info("[ProposalSemanticAnalyzer] 文档对 {} - {} 无候选片段，跳过", leftDocId, rightDocId);
                continue;
            }

            log.info("[ProposalSemanticAnalyzer] 文档对 {} - {} 召回 {} 组候选片段，开始 LLM 语义判断",
                    leftDocId, rightDocId, candidates.size());

            // 4. 对每组候选片段发起 LLM 裁决
            for (TenderSemanticJudgeReq candidate : candidates) {
                try {
                    TenderSemanticJudgeResp resp = reviewService.judge(candidate);

                    // 5. 将 LLM 响应转换为 RuleHit
                    RuleHit hit = convertToRuleHit(resp, data, leftDocId, rightDocId);
                    if (hit != null) {
                        allHits.add(hit);
                        log.info("[ProposalSemanticAnalyzer] 文档对 {} - {} LLM 判断命中 - confidence: {}, hit: {}",
                                leftDocId, rightDocId, resp.getConfidence(), resp.getHit());
                    }
                } catch (Exception e) {
                    log.error("[ProposalSemanticAnalyzer] LLM 裁决异常 - leftDoc: {}, rightDoc: {}, error: {}",
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

        // 简单取前两个文档作为比对对象
        // 实际场景中可能需要根据 compareScopes 确定文档对
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
     * <p>注意：置信度低于 0.70 的结果不直接触发高风险，仅作为辅助参考。
     * RuleHit 的来源通过 triggerSummary 中的 [LLM_SEMANTIC_RULE] 标记区分。
     */
    private RuleHit convertToRuleHit(TenderSemanticJudgeResp resp, TenderReviewData data,
                                      String leftDocId, String rightDocId) {
        // 如果置信度低于阈值，不直接命中（但记录日志供分析）
        if (resp.getConfidence() == null || resp.getConfidence() < MEDIUM_CONFIDENCE_THRESHOLD) {
            log.info("[ProposalSemanticAnalyzer] LLM 置信度 {} 低于阈值 {}，不直接触发规则命中，仅作辅助参考",
                    resp.getConfidence(), MEDIUM_CONFIDENCE_THRESHOLD);
            // 返回一个低权重命中，用于辅助参考但不打高风险
            return RuleHit.builder()
                    .hitId(UUID.randomUUID().toString())
                    .ruleCode(ANALYZER_CODE)
                    .ruleName(RULE_NAME)
                    .riskType(RISK_TYPE)
                    .priority("LOW")
                    .weight(0)
                    .confidence(resp.getConfidence())
                    .triggerSummary(String.format("[LLM_SEMANTIC_RULE] W-P1 技术方案疑似同源（置信度%.2f，建议人工复核）", resp.getConfidence()))
                    .documentIds(List.of(leftDocId, rightDocId))
                    .evidences(buildEvidences(resp, data))
                    .build();
        }

        // 根据置信度确定优先级
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
                .triggerSummary(String.format("[LLM_SEMANTIC_RULE] W-P1 技术方案语义同源 - %s", resp.getConclusion()))
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
                        .build())
                .toList();
    }
}
