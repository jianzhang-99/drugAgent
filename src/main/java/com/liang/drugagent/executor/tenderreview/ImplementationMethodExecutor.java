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
public class ImplementationMethodExecutor implements TenderRuleExecutor {

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

    /**
     * 执行实施方法相似性检测。
     *
     * @param data 标书审查结构化输入数据
     * @return 包含命中风险的结果集
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
     * 在指定比对范围内检测实施方法顺序的一致性。
     */
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
            for (int j = i + 1; j < documentIds.size(); j++) {
                String leftId = documentIds.get(i);
                String rightId = documentIds.get(j);
                List<Field> leftFields = fieldsByDoc.getOrDefault(leftId, List.of());
                List<Field> rightFields = fieldsByDoc.getOrDefault(rightId, List.of());

                if (isMethodSequenceIdentical(leftFields, rightFields)) {
                    hits.add(buildHit(scope, leftFields, rightFields));
                }
            }
        }
        return hits;
    }

    /**
     * 判断两个文档的实施阶段及其顺序是否完全一致。
     */
    private boolean isMethodSequenceIdentical(List<Field> left, List<Field> right) {
        if (left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
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

    /**
     * 构建抄袭命中项。
     */
    private RuleHit buildHit(CompareScope scope, List<Field> left, List<Field> right) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(90);

        String sequence = left.stream()
                .map(Field::getNormalizedValue)
                .collect(Collectors.joining(" -> "));
        hit.setMatchedValue("sequence:" + sequence);

        hit.setTriggerSummary(String.format("文档 %s 与 %s 的实施阶段划分（%s）及评审确认环节完全一致，存在高度同源风险。",
                left.get(0).getDocumentId(), right.get(0).getDocumentId(), sequence));

        hit.setDocumentIds(List.of(left.get(0).getDocumentId(), right.get(0).getDocumentId()));

        List<String> fieldIds = new ArrayList<>();
        left.forEach(f -> fieldIds.add(f.getFieldId()));
        right.forEach(f -> fieldIds.add(f.getFieldId()));
        hit.setFieldIds(fieldIds);

        hit.setBlockIds(fieldIds.stream()
                .map(id -> findBlockId(id, left, right))
                .filter(Objects::nonNull).distinct().toList());

        List<RuleEvidence> evidences = new ArrayList<>();
        left.forEach(f -> evidences.add(toEvidence(f)));
        right.forEach(f -> evidences.add(toEvidence(f)));
        hit.setEvidences(evidences);

        hit.setVersion(VERSION);
        return hit;
    }

    /**
     * 根据字段 ID 查找对应的块 ID。
     */
    private String findBlockId(String fieldId, List<Field> left, List<Field> right) {
        for (Field f : left) {
            if (f.getFieldId().equals(fieldId))
                return f.getBlockId();
        }
        for (Field f : right) {
            if (f.getFieldId().equals(fieldId))
                return f.getBlockId();
        }
        return null;
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
