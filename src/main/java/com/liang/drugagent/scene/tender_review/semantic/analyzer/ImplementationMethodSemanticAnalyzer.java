package com.liang.drugagent.scene.tender_review.semantic.analyzer;

import com.liang.drugagent.scene.tender_review.model.*;
import com.liang.drugagent.scene.tender_review.model.semantic.TenderSemanticEvidence;
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
import java.util.stream.Collectors;

/**
 * 实施方法语义分析器（W-P2）。
 *
 * <p>职责：对两份标书的实施方法章节进行 LLM 语义判断，识别阶段名称不同但流程骨架一致、
 * 关键里程碑和交付顺序同源、组织方式同源等软性抄袭特征。
 *
 * <p>不命中典型情况：
 * <ul>
 *   <li>仅包含行业标准实施流程（如"需求分析-设计-开发-测试-上线"通用五阶段）</li>
 *   <li>仅包含法规要求的标准步骤</li>
 *   <li>实施方法差异明显，具有独立编写痕迹</li>
 * </ul>
 *
 * <p>命中来源标记：由于 RuleHit 当前无 hitSource 字段，本类产生的命中应在日志和备注中标注来源为 LLM_SEMANTIC_RULE。
 *
 * @author phase2-developer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImplementationMethodSemanticAnalyzer implements TenderSemanticAnalyzer {

    private static final String ANALYZER_CODE = "W-P2";
    private static final String COMPARE_TOPIC = "实施方法";
    private static final String RULE_NAME = "实施方法抄袭";
    private static final String RISK_TYPE = "plagiarism";
    private static final String PRIORITY = "MEDIUM_HIGH";
    private static final String VERSION = "v1-llm";

    private final TenderSemanticCandidateService candidateService;
    private final TenderSemanticReviewService reviewService;

    @Override
    public String analyzerCode() {
        return ANALYZER_CODE;
    }

    @Override
    public List<RuleHit> analyze(TenderReviewData data) {
        if (data == null || data.getCompareScopes() == null || data.getCompareScopes().isEmpty()) {
            log.info("[ImplementationMethodSemanticAnalyzer] 无可比对范围，返回空命中列表");
            return List.of();
        }

        List<RuleHit> allHits = new ArrayList<>();
        String caseId = data.getACase() != null ? data.getACase().getCaseId() : "unknown";

        for (CompareScope scope : data.getCompareScopes()) {
            if (scope.getDocumentIds() == null || scope.getDocumentIds().size() < 2) {
                continue;
            }

            List<String> docIds = scope.getDocumentIds();
            // 两两比对文档
            for (int i = 0; i < docIds.size(); i++) {
                for (int j = i + 1; j < docIds.size(); j++) {
                    String leftDocId = docIds.get(i);
                    String rightDocId = docIds.get(j);

                    // 召回候选片段对
                    List<TenderSemanticJudgeReq> candidates = candidateService.recallCandidates(
                            data, ANALYZER_CODE, COMPARE_TOPIC, leftDocId, rightDocId
                    );

                    if (candidates.isEmpty()) {
                        log.debug("[ImplementationMethodSemanticAnalyzer] caseId={}, leftDoc={}, rightDoc={} 无候选片段",
                                caseId, leftDocId, rightDocId);
                        continue;
                    }

                    log.info("[ImplementationMethodSemanticAnalyzer] caseId={}, leftDoc={}, rightDoc={}, 候选数={}",
                            caseId, leftDocId, rightDocId, candidates.size());

                    // 逐个候选发起 LLM 裁决
                    for (TenderSemanticJudgeReq candidate : candidates) {
                        TenderSemanticJudgeResp resp = reviewService.judge(candidate);

                        // 置信度 < 0.70 不直接触发高风险，仅作为辅助证据
                        if (!Boolean.TRUE.equals(resp.getHit()) || resp.getConfidence() == null || resp.getConfidence() < 0.60) {
                            log.debug("[ImplementationMethodSemanticAnalyzer] caseId={}, 候选未命中或置信度不足 confidence={}",
                                    caseId, resp.getConfidence());
                            continue;
                        }

                        RuleHit hit = convertToRuleHit(resp, scope, candidate);
                        allHits.add(hit);
                        log.info("[ImplementationMethodSemanticAnalyzer] caseId={}, 命中规则 W-P2, confidence={}, conclusion={}",
                                caseId, resp.getConfidence(), resp.getConclusion());
                    }
                }
            }
        }

        return allHits;
    }

    /**
     * 将 LLM 裁决响应转换为 RuleHit。
     * 命中来源标记为 LLM_SEMANTIC_RULE。
     */
    private RuleHit convertToRuleHit(TenderSemanticJudgeResp resp, CompareScope scope, TenderSemanticJudgeReq req) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(ANALYZER_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope != null ? scope.getScopeId() : null);
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setVersion(VERSION);
        hit.setWeight(resp.getSuggestedWeight() != null ? resp.getSuggestedWeight() : 80);
        hit.setConfidence(resp.getConfidence());
        hit.setMatchedValue("semantic:" + resp.getConclusion());

        String triggerSummary = String.format("两份标书实施方法章节存在语义同源特征（置信度 %.2f）：%s",
                resp.getConfidence(), resp.getConclusion());
        hit.setTriggerSummary(triggerSummary);

        hit.setDocumentIds(List.of(req.getLeftDocumentId(), req.getRightDocumentId()));

        // 转换证据
        if (resp.getEvidences() != null) {
            List<RuleEvidence> evidences = resp.getEvidences().stream()
                    .map(this::convertToRuleEvidence)
                    .collect(Collectors.toList());
            hit.setEvidences(evidences);
        }

        // 记录来源标注（RuleHit 当前无 hitSource 字段，通过日志和注释标注）
        log.info("[ImplementationMethodSemanticAnalyzer] LLM_SEMANTIC_RULE hit created - ruleCode=W-P2, confidence={}, reason={}",
                resp.getConfidence(), resp.getReason());

        return hit;
    }

    /**
     * 将语义裁决证据转换为规则证据。
     */
    private RuleEvidence convertToRuleEvidence(TenderSemanticEvidence semEvidence) {
        if (semEvidence == null) {
            return null;
        }
        RuleEvidence evidence = new RuleEvidence();
        evidence.setDocumentId(semEvidence.getDocumentId());
        evidence.setChapterPath(semEvidence.getChapterPath());
        // matchedValue 使用摘录内容
        evidence.setMatchedValue(semEvidence.getExcerpt());
        return evidence;
    }
}
