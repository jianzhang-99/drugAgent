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
 * 风险识别抄袭执行器 (W-P4)。
 *
 * <p>
 * 识别不同投标文件的风险项描述、风险影响及应对措施是否高度相似（相似度系数 > 0.90）。
 * 该规则旨在检测针对项目特定风险点描述的同源性风险。
 * </p>
 *
 * @author liangjiajian
 */
@Component
public class RiskIdentificationExecutor implements TenderRuleExecutor {

    /** 风险识别字段类型。 */
    private static final String FIELD_TYPE = "risk_identification";
    /** 规则编码。 */
    private static final String RULE_CODE = "W-P4";
    /** 规则名称。 */
    private static final String RULE_NAME = "风险识别抄袭";
    /** 风险类型。 */
    private static final String RISK_TYPE = "plagiarism";
    /** 规则优先级。 */
    private static final String PRIORITY = "MEDIUM_HIGH";
    /** 规则版本。 */
    private static final String VERSION = "v1";

    /** 判定抄袭的相似度阈值系数。 */
    private static final double SIMILARITY_THRESHOLD = 0.90;

    /**
     * 执行风险识别项相似性检测。
     *
     * @param data 标书审查结构化输入数据
     * @return 命中风险的结果集
     */
    @Override
    public RuleResult execute(TenderReviewData data) {
        RuleResult result = new RuleResult();
        if (data == null || data.getCompareScopes() == null || data.getFields() == null) {
            return result;
        }

        List<RuleHit> hits = new ArrayList<>();
        for (CompareScope scope : data.getCompareScopes()) {
            hits.addAll(detectInScope(scope, data.getFields()));
        }
        result.setHits(hits);
        return result;
    }

    /**
     * 在指定比对范围内检测文本高度相似的风险项。
     */
    private List<RuleHit> detectInScope(CompareScope scope, List<Field> fields) {
        if (scope == null || scope.getDocumentIds() == null || scope.getDocumentIds().size() < 2) {
            return List.of();
        }

        // 按文档归集风险识别类字段
        Map<String, List<Field>> fieldsByDoc = fields.stream()
                .filter(Objects::nonNull)
                .filter(field -> FIELD_TYPE.equals(field.getFieldType()))
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();

        // 两两比对不同厂商的标书文档
        for (int i = 0; i < documentIds.size(); i++) {
            for (int j = i + 1; j < documentIds.size(); j++) {
                String leftId = documentIds.get(i);
                String rightId = documentIds.get(j);
                List<Field> leftFields = fieldsByDoc.getOrDefault(leftId, List.of());
                List<Field> rightFields = fieldsByDoc.getOrDefault(rightId, List.of());

                for (Field l : leftFields) {
                    for (Field r : rightFields) {
                        if (isHighlySimilar(l, r)) {
                            hits.add(buildHit(scope, l, r));
                        }
                    }
                }
            }
        }
        return hits;
    }

    /**
     * 判断两个风险项描述内容是否高度相似。
     */
    private boolean isHighlySimilar(Field f1, Field f2) {
        String v1 = f1.getNormalizedValue();
        String v2 = f2.getNormalizedValue();
        if (v1 == null || v2 == null) {
            return false;
        }
        return calculateSimilarity(v1, v2) >= SIMILARITY_THRESHOLD;
    }

    /**
     * 计算两个字符串的相似度平衡系数 (基于编辑距离)。
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
     * Levenshtein 编辑距离算法实现。
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
     * 构建风险识别抄袭命中项详情。
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

        hit.setTriggerSummary(String.format("文档 %s 与 %s 在风险项“%s”的内容描述、影响及应对措施上极其相似（相似度 %.0f%%）。",
                left.getDocumentId(), right.getDocumentId(), left.getNormalizedKey(), sim * 100));

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
