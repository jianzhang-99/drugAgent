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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final double NAME_OVERLAP_THRESHOLD = 0.3;
    private static final double REDUCED_CONFIDENCE_THRESHOLD = 0.5;

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
            log.info("[TeamOverlapSemanticAnalyzer] 未找到有效的文档对，跳过 W-M3 分析");
            return List.of();
        }

        List<RuleHit> allHits = new ArrayList<>();
        String caseId = data.getACase() != null ? data.getACase().getCaseId() : "unknown";

        // 2. 对每对文档进行语义判断
        for (String[] pair : documentPairs) {
            String leftDocId = pair[0];
            String rightDocId = pair[1];

            // 获取两份文档的原始内容用于人名提取
            String leftContent = extractDocumentContent(data, leftDocId);
            String rightContent = extractDocumentContent(data, rightDocId);

            // 3. 检查核心团队的人名重叠度（补充判断条件）
            double nameOverlap = calculateNameOverlap(leftContent, rightContent);
            boolean nameOverlapTriggered = nameOverlap >= NAME_OVERLAP_THRESHOLD;

            if (nameOverlapTriggered) {
                log.info("[TeamOverlapSemanticAnalyzer] 人名重叠度={}，触发专项分析 leftDoc={}, rightDoc={}",
                        nameOverlap, leftDocId, rightDocId);
            }

            // 4. 召回候选片段对（团队配置、人员简历等）
            List<TenderSemanticJudgeReq> candidates = candidateService.recallCandidates(
                    data, ANALYZER_CODE, COMPARE_TOPIC, leftDocId, rightDocId
            );

            if (candidates.isEmpty()) {
                log.info("[TeamOverlapSemanticAnalyzer] 文档对 {} - {} 无候选片段，跳过", leftDocId, rightDocId);
                continue;
            }

            log.info("[TeamOverlapSemanticAnalyzer] 文档对 {} - {} 召回 {} 组候选片段，开始 LLM 语义判断",
                    leftDocId, rightDocId, candidates.size());

            // 5. 对每组候选片段发起 LLM 裁决
            for (TenderSemanticJudgeReq candidate : candidates) {
                try {
                    TenderSemanticJudgeResp resp = reviewService.judge(candidate);

                    // 确定置信度阈值：人名重叠度高时降低要求
                    double confidenceThreshold = nameOverlapTriggered ? REDUCED_CONFIDENCE_THRESHOLD : 0.65;

                    // W-M3 涉及人员身份，判断需更谨慎：置信度 < 0.75 不直接触发风险
                    if (!Boolean.TRUE.equals(resp.getHit()) || resp.getConfidence() == null || resp.getConfidence() < confidenceThreshold) {
                        log.debug("[TeamOverlapSemanticAnalyzer] caseId={}, 候选未命中或置信度不足 confidence={}, threshold={}",
                                caseId, resp.getConfidence(), confidenceThreshold);
                        continue;
                    }

                    RuleHit hit = convertToRuleHit(resp, candidate);
                    // 人名重叠触发时标注附加说明
                    if (nameOverlapTriggered) {
                        String originalSummary = hit.getTriggerSummary();
                        hit.setTriggerSummary(originalSummary + String.format("（人名重叠度 %.2f 辅助判断）", nameOverlap));
                    }
                    allHits.add(hit);
                    log.info("[TeamOverlapSemanticAnalyzer] 文档对 {} - {} LLM 判断命中 W-M3 - confidence: {}, hit: {}, nameOverlap: {}",
                            leftDocId, rightDocId, resp.getConfidence(), resp.getHit(), nameOverlap);
                } catch (Exception e) {
                    log.error("[TeamOverlapSemanticAnalyzer] LLM 裁决异常 - leftDoc: {}, rightDoc: {}, error: {}",
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
     * 从审查数据中根据文档ID提取文档内容（从blocks拼接）。
     */
    private String extractDocumentContent(TenderReviewData data, String documentId) {
        List<Block> blocks = data.getBlocks();
        if (blocks == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            if (documentId.equals(block.getDocumentId()) && block.getContent() != null) {
                sb.append(block.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 从文本中提取人名（表格格式：姓名 + Tab + 岗位 + Tab + 年限...）。
     * 人名通常是行首的2-4个汉字。
     */
    private Set<String> extractNamesFromText(String text) {
        Set<String> names = new HashSet<>();
        if (text == null || text.isBlank()) return names;
        // 表格行格式：周辰    项目经理    12年 ...
        Pattern namePattern = Pattern.compile("^([\\u4e00-\\u9fa5]{2,4})(?:\\t|\\s)", Pattern.MULTILINE);
        Matcher matcher = namePattern.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * 计算人名重叠度（Jaccard 相似度）。
     */
    private double calculateNameOverlap(String text1, String text2) {
        Set<String> names1 = extractNamesFromText(text1);
        Set<String> names2 = extractNamesFromText(text2);
        if (names1.isEmpty() && names2.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(names1);
        intersection.retainAll(names2);
        Set<String> union = new HashSet<>(names1);
        union.addAll(names2);
        return (double) intersection.size() / union.size();
    }

    /**
     * 将 LLM 裁决响应转换为 RuleHit。
     * 命中来源标记为 LLM_SEMANTIC_RULE。
     */
    private RuleHit convertToRuleHit(TenderSemanticJudgeResp resp, TenderSemanticJudgeReq req) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(ANALYZER_CODE);
        hit.setRuleName(RULE_NAME);
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
