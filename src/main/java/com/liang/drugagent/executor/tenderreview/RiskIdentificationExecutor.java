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
public class RiskIdentificationExecutor extends AbstractTenderExecutor {

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
            String leftId = documentIds.get(i);
            List<Field> leftFields = fieldsByDoc.getOrDefault(leftId, List.of());
            if (leftFields.isEmpty())
                continue;

            for (int j = i + 1; j < documentIds.size(); j++) {
                String rightId = documentIds.get(j);
                List<Field> rightFields = fieldsByDoc.getOrDefault(rightId, List.of());
                if (rightFields.isEmpty())
                    continue;

                for (Field l : leftFields) {
                    for (Field r : rightFields) {
                        double similarity = getSimilarity(l, r);
                        if (similarity >= SIMILARITY_THRESHOLD) {
                            hits.add(buildHit(scope, l, r, similarity));
                        }
                    }
                }
            }
        }
        return hits;
    }

    private double getSimilarity(Field f1, Field f2) {
        String v1 = f1.getNormalizedValue();
        String v2 = f2.getNormalizedValue();
        if (v1 == null || v2 == null)
            return 0.0;
        if (v1.equals(v2))
            return 1.0;

        int longerLength = Math.max(v1.length(), v2.length());
        if (longerLength == 0)
            return 1.0;
        return (longerLength - editDistance(v1, v2)) / (double) longerLength;
    }

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

    private RuleHit buildHit(CompareScope scope, Field left, Field right, double sim) {
        RuleHit hit = createBaseHit(RULE_CODE, RULE_NAME, scope.getScopeId(), RISK_TYPE, PRIORITY, VERSION);
        hit.setWeight(85);
        hit.setMatchedValue("similarity:" + String.format("%.2f", sim));

        hit.setTriggerSummary(String.format("文档 %s 与 %s 在风险项“%s”的内容描述、影响及应对措施上极其相似（相似度 %.0f%%）。",
                left.getDocumentId(), right.getDocumentId(), left.getNormalizedKey(), sim * 100));

        hit.setDocumentIds(List.of(left.getDocumentId(), right.getDocumentId()));
        hit.setFieldIds(List.of(left.getFieldId(), right.getFieldId()));
        hit.setBlockIds(List.of(left.getBlockId(), right.getBlockId()).stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList()));

        hit.setEvidences(List.of(toEvidence(left), toEvidence(right)));
        return hit;
    }
}
