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
 * 联系方式近邻规则执行器。
 *
 * <p>当前版本聚焦 W-M2：
 * 在同一比对范围内，如果不同投标人的联系人姓名完全一致，
 * 且手机号码属于同一号段仅尾数（末位/末两位）不同，则判定为“联系方式近邻”。</p>
 *
 * <p>约定上游将联系人信息抽取为 `fieldType=contact_info` 的字段：
 * `normalizedKey` 标识联系人姓名，
 * `normalizedValue` 存放标准化的手机号码。</p>
 *
 * @author liangjiajian
 */
@Component
public class ContactProximityExecutor implements TenderRuleExecutor {

    /** 联系人信息字段类型。 */
    private static final String CONTACT_INFO_FIELD_TYPE = "contact_info";
    /** 规则编码。 */
    private static final String RULE_CODE = "W-M2";
    /** 规则名称。 */
    private static final String RULE_NAME = "联系方式近邻";
    /** 风险类型。 */
    private static final String RISK_TYPE = "collusion";
    /** 规则优先级。 */
    private static final String PRIORITY = "VERY_HIGH";
    /** 规则版本。 */
    private static final String VERSION = "v1";

    /**
     * 执行联系方式近邻识别。
     *
     * @param data 标书审查结构化输入
     * @return 命中结果
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

    private List<RuleHit> detectInScope(CompareScope scope, List<Field> fields) {
        if (scope == null || scope.getDocumentIds() == null || scope.getDocumentIds().size() < 2) {
            return List.of();
        }

        Map<String, List<Field>> contactFieldsByDocument = fields.stream()
                .filter(Objects::nonNull)
                .filter(field -> CONTACT_INFO_FIELD_TYPE.equals(field.getFieldType()))
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();
        for (int i = 0; i < documentIds.size(); i++) {
            for (int j = i + 1; j < documentIds.size(); j++) {
                String leftDocId = documentIds.get(i);
                String rightDocId = documentIds.get(j);
                List<Field> leftFields = contactFieldsByDocument.getOrDefault(leftDocId, List.of());
                List<Field> rightFields = contactFieldsByDocument.getOrDefault(rightDocId, List.of());

                for (Field leftField : leftFields) {
                    for (Field rightField : rightFields) {
                        if (isProximity(leftField, rightField)) {
                            hits.add(buildHit(scope, leftField, rightField));
                        }
                    }
                }
            }
        }
        return hits;
    }

    private boolean isProximity(Field f1, Field f2) {
        String name1 = f1.getNormalizedKey();
        String name2 = f2.getNormalizedKey();
        String phone1 = normalizePhone(f1.getNormalizedValue());
        String phone2 = normalizePhone(f2.getNormalizedValue());

        if (name1 == null || name2 == null || name1.isBlank() || name2.isBlank()) {
            return false;
        }
        if (phone1 == null || phone2 == null || phone1.isBlank() || phone2.isBlank()) {
            return false;
        }

        // 准则 1: 姓名一致 (忽略首尾空格)
        if (!name1.trim().equals(name2.trim())) {
            return false;
        }

        // 准则 2: 电话号码近邻
        // 如果完全一样，更是围标迹象
        if (phone1.equals(phone2)) {
            return true;
        }

        // 检查是否为号段相同，仅末位或末两位差异
        // 通常手机号 11 位，我们要求至少前 9 位相同
        if (phone1.length() == phone2.length() && phone1.length() >= 7) {
            int length = phone1.length();
            String prefix1 = phone1.substring(0, length - 2);
            String prefix2 = phone2.substring(0, length - 2);
            if (prefix1.equals(prefix2)) {
                return true;
            }
        }

        return false;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return null;
        // 去除连字符、空格等非数字字符
        return phone.replaceAll("[^0-9]", "");
    }

    private RuleHit buildHit(CompareScope scope, Field f1, Field f2) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(90);
        hit.setMatchedValue("contact_name:" + f1.getNormalizedKey() + ", phones:[" + f1.getNormalizedValue() + ", " + f2.getNormalizedValue() + "]");
        hit.setTriggerSummary(String.format("文档 %s 与 %s 的联系人姓名均为“%s”，且联系电话（%s, %s）高度相似，存在同号段近邻特征。",
                f1.getDocumentId(), f2.getDocumentId(), f1.getNormalizedKey(), f1.getNormalizedValue(), f2.getNormalizedValue()));
        hit.setDocumentIds(List.of(f1.getDocumentId(), f2.getDocumentId()));
        hit.setFieldIds(List.of(f1.getFieldId(), f2.getFieldId()));
        hit.setBlockIds(java.util.stream.Stream.of(f1.getBlockId(), f2.getBlockId())
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        hit.setEvidences(List.of(toEvidence(f1), toEvidence(f2)));
        hit.setVersion(VERSION);
        return hit;
    }

    private RuleEvidence toEvidence(Field field) {
        RuleEvidence evidence = new RuleEvidence();
        evidence.setDocumentId(field.getDocumentId());
        evidence.setFieldId(field.getFieldId());
        evidence.setBlockId(field.getBlockId());
        evidence.setMatchedValue(field.getNormalizedKey() + " (" + field.getNormalizedValue() + ")");
        evidence.setChapterPath(field.getChapterPath());
        evidence.setAnchor(field.getAnchor());
        return evidence;
    }
}
