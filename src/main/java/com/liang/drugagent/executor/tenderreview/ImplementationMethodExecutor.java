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
 * 实施方法抄袭执行器 (W-P2)。
 *
 * <p>
 * 识别不同投标文件的实施方法（阶段名称及顺序）是否实质性重合，
 * 重点检测调研、设计、开发、测试、试运行等标准阶段的排列一致性。
 * </p>
 *
 * @author liangjiajian
 */
@Component
public class ImplementationMethodExecutor extends AbstractTenderExecutor {

    /** 实施方法字段类型。 */
    private static final String FIELD_TYPE = "implementation_method";
    /** 规则编码。 */
    private static final String RULE_CODE = "W-P2";
    /** 规则名称。 */
    private static final String RULE_NAME = "实施方法抄袭";
    /** 风险类型。 */
    private static final String RISK_TYPE = "plagiarism";
    /** 规则优先级。 */
    private static final String PRIORITY = "MEDIUM_HIGH";
    /** 规则版本。 */
    private static final String VERSION = "v1";

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

        // 按文档聚合实施方法字段
        Map<String, List<Field>> fieldsByDoc = fields.stream()
                .filter(Objects::nonNull)
                .filter(field -> FIELD_TYPE.equals(field.getFieldType()))
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();

        // 两两比对文档
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

                if (isMethodSequenceIdentical(leftFields, rightFields)) {
                    hits.add(buildHit(scope, leftFields, rightFields));
                }
            }
        }
        return hits;
    }

    private boolean isMethodSequenceIdentical(List<Field> left, List<Field> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            String v1 = left.get(i).getNormalizedValue();
            String v2 = right.get(i).getNormalizedValue();
            if (v1 == null || !v1.equals(v2)) {
                return false;
            }
        }
        return true;
    }

    private RuleHit buildHit(CompareScope scope, List<Field> left, List<Field> right) {
        RuleHit hit = createBaseHit(RULE_CODE, RULE_NAME, scope.getScopeId(), RISK_TYPE, PRIORITY, VERSION);
        hit.setWeight(90);

        String sequence = left.stream()
                .map(Field::getNormalizedValue)
                .collect(Collectors.joining(" -> "));
        hit.setMatchedValue("sequence:" + sequence);

        hit.setTriggerSummary(String.format("文档 %s 与 %s 的实施阶段划分（%s）及评审确认环节完全一致，存在高度同源风险。",
                left.get(0).getDocumentId(), right.get(0).getDocumentId(), sequence));

        List<String> docIds = List.of(left.get(0).getDocumentId(), right.get(0).getDocumentId());
        hit.setDocumentIds(docIds);

        List<Field> allMatchedFields = new ArrayList<>(left);
        allMatchedFields.addAll(right);

        hit.setFieldIds(allMatchedFields.stream().map(Field::getFieldId).collect(Collectors.toList()));
        hit.setBlockIds(allMatchedFields.stream().map(Field::getBlockId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList()));
        hit.setEvidences(allMatchedFields.stream().map(this::toEvidence).collect(Collectors.toList()));

        return hit;
    }
}
