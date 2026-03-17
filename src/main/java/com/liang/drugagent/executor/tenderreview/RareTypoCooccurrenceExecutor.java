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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 罕见错误共现规则执行器。
 *
 * <p>当前版本聚焦 W-M5：
 * 在同一比对范围内，如果不同投标人的文件中出现了完全相同的罕见错别字（如“应急响映”），
 * 则判定为“罕见错误共现”。</p>
 *
 * <p>约定上游将错别字信息抽取为 `fieldType=typo` 的字段：
 * `normalizedKey` 标识纠正后的词（可选），
 * `normalizedValue` 存放发现的错别字原文。</p>
 *
 * @author liangjiajian
 */
@Component
public class RareTypoCooccurrenceExecutor implements TenderRuleExecutor {

    /** 错别字字段类型。 */
    private static final String TYPO_FIELD_TYPE = "typo";
    /** 规则编码。 */
    private static final String RULE_CODE = "W-M5";
    /** 规则名称。 */
    private static final String RULE_NAME = "罕见错误共现";
    /** 风险类型。 */
    private static final String RISK_TYPE = "collusion";
    /** 规则优先级。 */
    private static final String PRIORITY = "VERY_HIGH";
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

        Map<String, List<Field>> typoFieldsByDocument = fields.stream()
                .filter(Objects::nonNull)
                .filter(field -> TYPO_FIELD_TYPE.equals(field.getFieldType()))
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();
        for (int i = 0; i < documentIds.size(); i++) {
            for (int j = i + 1; j < documentIds.size(); j++) {
                String leftDocId = documentIds.get(i);
                String rightDocId = documentIds.get(j);
                List<Field> leftFields = typoFieldsByDocument.getOrDefault(leftDocId, List.of());
                List<Field> rightFields = typoFieldsByDocument.getOrDefault(rightDocId, List.of());

                hits.addAll(detectBetweenDocuments(scope, leftDocId, leftFields, rightDocId, rightFields));
            }
        }
        return hits;
    }

    private List<RuleHit> detectBetweenDocuments(CompareScope scope, String leftDocId, List<Field> leftFields,
                                                 String rightDocId, List<Field> rightFields) {
        if (leftFields.isEmpty() || rightFields.isEmpty()) {
            return List.of();
        }

        List<RuleHit> hits = new ArrayList<>();
        // 按照错别字内容分组，查找共现的错别字
        Map<String, List<Field>> leftByTypo = leftFields.stream().collect(Collectors.groupingBy(Field::getNormalizedValue));
        Map<String, List<Field>> rightByTypo = rightFields.stream().collect(Collectors.groupingBy(Field::getNormalizedValue));

        Set<String> commonTypos = leftByTypo.keySet().stream().filter(rightByTypo::containsKey).collect(Collectors.toSet());

        for (String typo : commonTypos) {
            hits.add(buildHit(scope, leftDocId, rightDocId, typo, leftByTypo.get(typo), rightByTypo.get(typo)));
        }

        return hits;
    }

    private RuleHit buildHit(CompareScope scope, String leftDocId, String rightDocId, String typo,
                              List<Field> leftFields, List<Field> rightFields) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(98); // 罕见错误共现是极高权重特征
        hit.setMatchedValue("common_typo:" + typo);
        hit.setTriggerSummary(String.format("文档 %s 与 %s 共同出现罕见错别字“%s”。低频错别字在不同投标主体中同步出现，是判定文档同源/代写的极高权重特征。",
                leftDocId, rightDocId, typo));

        hit.setDocumentIds(List.of(leftDocId, rightDocId));
        
        List<String> fieldIds = new ArrayList<>();
        List<RuleEvidence> evidences = new ArrayList<>();
        
        for (Field f : leftFields) {
            fieldIds.add(f.getFieldId());
            evidences.add(toEvidence(f));
        }
        for (Field f : rightFields) {
            fieldIds.add(f.getFieldId());
            evidences.add(toEvidence(f));
        }

        hit.setFieldIds(fieldIds.stream().distinct().toList());
        hit.setEvidences(evidences);
        hit.setVersion(VERSION);
        return hit;
    }

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
