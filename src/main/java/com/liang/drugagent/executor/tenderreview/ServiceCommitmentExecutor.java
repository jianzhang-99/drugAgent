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
 * 服务承诺抄袭执行器 (W-P3)。
 *
 * <p>
 * 识别不同投标文件的服务响应时效（如普通问题 4h、重大问题 2h、疑难问题 24h）是否完全重合，
 * 旨在发现服务体系描述中的同源性风险。
 * </p>
 *
 * @author liangjiajian
 */
@Component
public class ServiceCommitmentExecutor implements TenderRuleExecutor {

    /** 服务承诺字段类型。 */
    private static final String FIELD_TYPE = "service_commitment";
    /** 规则编码。 */
    private static final String RULE_CODE = "W-P3";
    /** 规则名称。 */
    private static final String RULE_NAME = "服务承诺抄袭";
    /** 风险类型。 */
    private static final String RISK_TYPE = "plagiarism";
    /** 规则优先级。 */
    private static final String PRIORITY = "MEDIUM_HIGH";
    /** 规则版本。 */
    private static final String VERSION = "v1";

    /**
     * 执行服务承诺相似性检测。
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
     * 在指定比对范围内检测承诺内容是否完全一致。
     */
    private List<RuleHit> detectInScope(CompareScope scope, List<Field> fields) {
        if (scope == null || scope.getDocumentIds() == null || scope.getDocumentIds().size() < 2) {
            return List.of();
        }

        // 按文档聚合服务承诺字段
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

                hits.addAll(compareCommitments(scope, leftFields, rightFields));
            }
        }
        return hits;
    }

    /**
     * 对比两份文档中的服务承诺项。
     */
    private List<RuleHit> compareCommitments(CompareScope scope, List<Field> left, List<Field> right) {
        List<RuleHit> hits = new ArrayList<>();
        for (Field l : left) {
            for (Field r : right) {
                // key 匹配（如“重大问题响应时间”）且 value 匹配（如“2h”）
                if (Objects.equals(l.getNormalizedKey(), r.getNormalizedKey()) &&
                        Objects.equals(l.getNormalizedValue(), r.getNormalizedValue()) &&
                        l.getNormalizedValue() != null) {
                    hits.add(buildHit(scope, l, r));
                }
            }
        }
        return hits;
    }

    /**
     * 构建服务承诺抄袭命中项。
     */
    private RuleHit buildHit(CompareScope scope, Field left, Field right) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(88);

        hit.setMatchedValue(String.format("%s:%s", left.getNormalizedKey(), left.getNormalizedValue()));
        hit.setTriggerSummary(String.format("文档 %s 与 %s 在“%s”的服务承诺时效上完全重合（数值均为 %s），存在抄袭风险。",
                left.getDocumentId(), right.getDocumentId(), left.getNormalizedKey(), left.getNormalizedValue()));

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
