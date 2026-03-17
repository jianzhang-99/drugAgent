package com.liang.drugagent.executor.tenderreview;

import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class ProposalPlagiarismExecutor extends AbstractTenderExecutor {

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
        for (CompareScope scope : data.getCompareScopes()) {
            hits.addAll(detectInScope(scope, data.getFields()));
        }
        result.setHits(hits);
        return result;
    }

    /**
     * 在指定比对范围内检测文档。
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
            String leftDocId = documentIds.get(i);
            List<Field> leftFields = proposalFieldsByDoc.getOrDefault(leftDocId, List.of());
            if (leftFields.isEmpty())
                continue;

            for (int j = i + 1; j < documentIds.size(); j++) {
                String rightDocId = documentIds.get(j);
                List<Field> rightFields = proposalFieldsByDoc.getOrDefault(rightDocId, List.of());
                if (rightFields.isEmpty())
                    continue;

                for (Field left : leftFields) {
                    for (Field right : rightFields) {
                        double similarity = calculateSimilarity(left.getNormalizedValue(), right.getNormalizedValue());
                        if (isHighlySimilar(left, right, similarity)) {
                            hits.add(buildHit(scope, left, right, similarity));
                        }
                    }
                }
            }
        }
        return hits;
    }

    /**
     * 判断两个字段内容是否实质性相似。
     */
    private boolean isHighlySimilar(Field f1, Field f2, double similarity) {
        String v1 = f1.getNormalizedValue();
        if (v1 == null)
            return false;

        // 特殊业务规则判断
        if (v1.contains("统一门户") && v1.contains("统一流程") && v1.contains("统一数据口径")) {
            return similarity >= SIMILARITY_THRESHOLD;
        }

        return similarity >= SIMILARITY_THRESHOLD;
    }

    /**
     * 计算两个字符串的相似度。
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null)
            return 0.0;
        if (s1.equals(s2))
            return 1.0;

        int longerLength = Math.max(s1.length(), s2.length());
        if (longerLength == 0)
            return 1.0;

        return (longerLength - editDistance(s1, s2)) / (double) longerLength;
    }

    /**
     * 使用编辑距离算法。
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
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }

    /**
     * 构建命中汇总。
     */
    private RuleHit buildHit(CompareScope scope, Field left, Field right, double sim) {
        RuleHit hit = createBaseHit(RULE_CODE, RULE_NAME, scope.getScopeId(), RISK_TYPE, PRIORITY, VERSION);
        hit.setWeight(85);
        hit.setMatchedValue("similarity:" + String.format("%.2f", sim));

        hit.setTriggerSummary(String.format("文档 %s 与 %s 在技术方案中关于“%s”的描述实质性相似（相似度 %.0f%%）。",
                left.getDocumentId(), right.getDocumentId(), left.getFieldName(), sim * 100));

        hit.setDocumentIds(List.of(left.getDocumentId(), right.getDocumentId()));
        hit.setFieldIds(List.of(left.getFieldId(), right.getFieldId()));
        hit.setBlockIds(List.of(left.getBlockId(), right.getBlockId()).stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList()));

        hit.setEvidences(List.of(toEvidence(left), toEvidence(right)));
        return hit;
    }
}
