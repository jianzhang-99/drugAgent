package com.liang.drugagent.executor.tenderreview;

import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 技术方案抄袭执行器 (W-P1)。
 *
 * <p>
 * 识别不同投标文件的技术方案段落中是否存在高度相似的内容，
 * 重点检测核心业务描述的特征点是否重合。
 * </p>
 *
 * @author liangjiajian
 */
@Component
public class ProposalPlagiarismExecutor implements TenderRuleExecutor {

    /** 技术方案字段类型。 */
    private static final String FIELD_TYPE = "proposal_segment";
    /** 规则编码。 */
    private static final String RULE_CODE = "W-P1";
    /** 规则名称。 */
    private static final String RULE_NAME = "技术方案抄袭";
    /** 风险类型。 */
    private static final String RISK_TYPE = "plagiarism";
    /** 规则优先级。 */
    private static final String PRIORITY = "MEDIUM_HIGH";
    /** 规则版本。 */
    private static final String VERSION = "v1";

    /** 判定抄袭的相似度阈值。 */
    private static final double SIMILARITY_THRESHOLD = 0.90;

    /**
     * 执行技术方案相似性检测。
     *
     * @param data 标书审查结构化输入数据
     * @return 包含所有命中抄袭风险的结果集
     */
    @Override
    public RuleResult execute(TenderReviewData data) {
        RuleResult result = new RuleResult();
        if (data == null || data.getCompareScopes() == null || data.getFields() == null) {
            return result;
        }

        List<RuleHit> hits = new ArrayList<>();
        // 在每个比对范围内循环执行检测
        for (CompareScope scope : data.getCompareScopes()) {
            hits.addAll(detectInScope(scope, data.getFields()));
        }
        result.setHits(hits);
        return result;
    }

    /**
     * 在指定比对范围内检测文档。
     *
     * @param scope  比对范围
     * @param fields 所有字段列表
     * @return 命中项列表
     */
    private List<RuleHit> detectInScope(CompareScope scope, List<Field> fields) {
        if (scope == null || scope.getDocumentIds() == null || scope.getDocumentIds().size() < 2) {
            return List.of();
        }

        // 按文档归集技术方案类型的字段
        Map<String, List<Field>> proposalFieldsByDoc = fields.stream()
                .filter(Objects::nonNull)
                .filter(field -> FIELD_TYPE.equals(field.getFieldType()))
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();
        // 两两比对不同厂商的标书文档
        for (int i = 0; i < documentIds.size(); i++) {
            for (int j = i + 1; j < documentIds.size(); j++) {
                String leftDocId = documentIds.get(i);
                String rightDocId = documentIds.get(j);
                List<Field> leftFields = proposalFieldsByDoc.getOrDefault(leftDocId, List.of());
                List<Field> rightFields = proposalFieldsByDoc.getOrDefault(rightDocId, List.of());

                for (Field left : leftFields) {
                    for (Field right : rightFields) {
                        if (isHighlySimilar(left, right)) {
                            hits.add(buildHit(scope, left, right));
                        }
                    }
                }
            }
        }
        return hits;
    }

    /**
     * 判断两个字段内容是否实质性相似。
     *
     * @param f1 字段 A
     * @param f2 字段 B
     * @return 是否相似
     */
    private boolean isHighlySimilar(Field f1, Field f2) {
        String v1 = f1.getNormalizedValue();
        String v2 = f2.getNormalizedValue();
        if (v1 == null || v2 == null) {
            return false;
        }

        double similarity = calculateSimilarity(v1, v2);

        // 如果包含特定高频业务环节描述关键词，降低误报可能或作为加成判断
        // (W-P1 典型场景：统一门户、统一流程、统一数据口径)
        if (v1.contains("统一门户") && v1.contains("统一流程") && v1.contains("统一数据口径")) {
            return similarity >= SIMILARITY_THRESHOLD;
        }

        return similarity >= SIMILARITY_THRESHOLD;
    }

    /**
     * 计算两个字符串的相似度（Levenshtein 算法基础上的标准化相似度）。
     *
     * @param s1 字符串 1
     * @param s2 字符串 2
     * @return 相似度 (0.0 to 1.0)
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }
        int longerLength = Math.max(s1.length(), s2.length());
        if (longerLength == 0) {
            return 1.0;
        }
        return (longerLength - editDistance(s1, s2)) / (double) longerLength;
    }

    /**
     * 计算两个字符串之间的编辑距离。
     */
    private int editDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }

    /**
     * 构建命中风险详情。
     */
    private RuleHit buildHit(CompareScope scope, Field left, Field right) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(85);

        double sim = calculateSimilarity(left.getNormalizedValue(), right.getNormalizedValue());
        hit.setMatchedValue("similarity:" + String.format("%.2f", sim));

        hit.setTriggerSummary(String.format("文档 %s 与 %s 在技术方案中关于“%s”的描述实质性相似（相似度 %.0f%%）。",
                left.getDocumentId(), right.getDocumentId(), left.getFieldName(), sim * 100));

        hit.setDocumentIds(List.of(left.getDocumentId(), right.getDocumentId()));
        hit.setFieldIds(List.of(left.getFieldId(), right.getFieldId()));
        hit.setBlockIds(List.of(left.getBlockId(), right.getBlockId()).stream()
                .filter(Objects::nonNull).distinct().toList());
        hit.setEvidences(List.of(toEvidence(left), toEvidence(right)));
        hit.setVersion(VERSION);
        return hit;
    }

    /**
     * 转换证据对象。
     */
    private RuleEvidence toEvidence(Field field) {
        RuleEvidence evidence = new RuleEvidence();
        evidence.setDocumentId(field.getDocumentId());
        evidence.setFieldId(field.getFieldId());
        evidence.setBlockId(field.getBlockId());
        evidence.setMatchedValue(field.getNormalizedValue());
        evidence.setChapterPath(field.getChapterPath());
        evidence.setAnchor(field.getAnchor());
        return evidence;
    }
}
