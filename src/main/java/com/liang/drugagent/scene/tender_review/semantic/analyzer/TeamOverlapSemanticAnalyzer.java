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
 * 核心团队重叠语义分析器（W-M3）。
 *
 * <p>职责：对两份标书的核心团队章节进行 LLM 语义辅助判断，识别以下软性重叠特征：
 * <ul>
 *   <li>同一人不同岗位包装（姓名相似 + 从业年限相似 + 资质证书相似 → 疑似同一人）</li>
 *   <li>简历表达改写但履历骨架一致</li>
 *   <li>团队构成关系相似</li>
 * </ul>
 *
 * <p>不命中典型情况：
 * <ul>
 *   <li>仅行业通用人员配置标准（如"至少配备 3 名项目经理"等标准化要求）</li>
 *   <li>相同岗位名称但人员配置明显不同</li>
 *   <li>团队构成存在实质差异，具有独立组建痕迹</li>
 * </ul>
 *
 * <p>注意：W-M3 核心团队重叠涉及人员身份比对，属于高敏感场景。
 * LLM 辅助判断仅作为参考，最终结论应结合其他证据综合判定。
 *
 * <p>命中来源标记：由于 RuleHit 当前无 hitSource 字段，本类产生的命中应在日志和备注中标注来源为 LLM_SEMANTIC_RULE。
 *
 * @author phase2-developer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamOverlapSemanticAnalyzer implements TenderSemanticAnalyzer {

    private static final String ANALYZER_CODE = "W-M3";
    private static final String COMPARE_TOPIC = "核心团队";
    private static final String RULE_NAME = "核心团队重叠";
    private static final String RISK_TYPE = "team_overlap";
    private static final String PRIORITY = "HIGH";
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
            log.info("[TeamOverlapSemanticAnalyzer] 无可比对范围，返回空命中列表");
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

                    // 召回候选片段对（团队配置、人员简历等）
                    List<TenderSemanticJudgeReq> candidates = candidateService.recallCandidates(
                            data, ANALYZER_CODE, COMPARE_TOPIC, leftDocId, rightDocId
                    );

                    if (candidates.isEmpty()) {
                        log.debug("[TeamOverlapSemanticAnalyzer] caseId={}, leftDoc={}, rightDoc={} 无候选片段",
                                caseId, leftDocId, rightDocId);
                        continue;
                    }

                    log.info("[TeamOverlapSemanticAnalyzer] caseId={}, leftDoc={}, rightDoc={}, 候选数={}",
                            caseId, leftDocId, rightDocId, candidates.size());

                    // 逐个候选发起 LLM 裁决
                    for (TenderSemanticJudgeReq candidate : candidates) {
                        TenderSemanticJudgeResp resp = reviewService.judge(candidate);

                        // W-M3 涉及人员身份，判断需更谨慎：置信度 < 0.75 不直接触发风险
                        if (!Boolean.TRUE.equals(resp.getHit()) || resp.getConfidence() == null || resp.getConfidence() < 0.75) {
                            log.debug("[TeamOverlapSemanticAnalyzer] caseId={}, 候选未命中或置信度不足 confidence={}",
                                    caseId, resp.getConfidence());
                            continue;
                        }

                        RuleHit hit = convertToRuleHit(resp, scope, candidate);
                        allHits.add(hit);
                        log.info("[TeamOverlapSemanticAnalyzer] caseId={}, 命中规则 W-M3, confidence={}, conclusion={}",
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
        hit.setWeight(resp.getSuggestedWeight() != null ? resp.getSuggestedWeight() : 85);
        hit.setConfidence(resp.getConfidence());
        hit.setMatchedValue("semantic:" + resp.getConclusion());

        String triggerSummary = String.format("两份标书核心团队存在语义重叠特征（置信度 %.2f）：%s",
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
        log.info("[TeamOverlapSemanticAnalyzer] LLM_SEMANTIC_RULE hit created - ruleCode=W-M3, confidence={}, reason={}",
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
        evidence.setMatchedValue(semEvidence.getExcerpt());
        evidence.setOriginalValue(semEvidence.getExcerpt());
        return evidence;
    }
}
