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
 * 联系方式近邻执行器 (W-M2)。
 *
 * <p>
 * 识别不同投标文件的联系人是否同名，且电话号码极其相似（如尾数差异在 2 位以内）。
 * 该规则旨在检测不同投标单位使用同一套联系人及相近通讯号码的关联风险。
 * </p>
 *
 * @author liangjiajian
 */
@Component
public class ContactNearbyExecutor implements TenderRuleExecutor {

    /** 联系人姓名。 */
    private static final String CONTACT_PERSON_TYPE = "contact_person";
    /** 联系电话。 */
    private static final String CONTACT_PHONE_TYPE = "contact_phone";
    /** 规则编码。 */
    private static final String RULE_CODE = "W-M2";
    /** 规则名称。 */
    private static final String RULE_NAME = "联系方式近邻";
    /** 风险类型。 */
    private static final String RISK_TYPE = "collusion";
    /** 规则优先级。 */
    private static final String PRIORITY = "HIGH";
    /** 规则版本。 */
    private static final String VERSION = "v1";

    /**
     * 执行联系方式关联检测。
     *
     * @param data 标书审查结构化输入数据
     * @return 命中项结果
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
     * 在比对范围内搜索关联的联系信息。
     */
    private List<RuleHit> detectInScope(CompareScope scope, List<Field> fields) {
        if (scope == null || scope.getDocumentIds() == null || scope.getDocumentIds().size() < 2) {
            return List.of();
        }

        // 按文档聚合联系相关字段
        Map<String, List<Field>> contactFieldsByDoc = fields.stream()
                .filter(Objects::nonNull)
                .filter(field -> CONTACT_PERSON_TYPE.equals(field.getFieldType())
                        || CONTACT_PHONE_TYPE.equals(field.getFieldType()))
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();

        // 执行文档间的两两比对
        for (int i = 0; i < documentIds.size(); i++) {
            for (int j = i + 1; j < documentIds.size(); j++) {
                String leftDocId = documentIds.get(i);
                String rightDocId = documentIds.get(j);
                List<Field> leftFields = contactFieldsByDoc.getOrDefault(leftDocId, List.of());
                List<Field> rightFields = contactFieldsByDoc.getOrDefault(rightDocId, List.of());

                RuleHit hit = compareContacts(scope, leftDocId, leftFields, rightDocId, rightFields);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    /**
     * 对比两份文档中的具体联系姓名和电话项。
     */
    private RuleHit compareContacts(CompareScope scope, String leftId, List<Field> leftFields, String rightId,
            List<Field> rightFields) {
        // 预先分类
        List<Field> leftNames = leftFields.stream().filter(f -> CONTACT_PERSON_TYPE.equals(f.getFieldType())).toList();
        List<Field> leftPhones = leftFields.stream().filter(f -> CONTACT_PHONE_TYPE.equals(f.getFieldType())).toList();
        List<Field> rightNames = rightFields.stream().filter(f -> CONTACT_PERSON_TYPE.equals(f.getFieldType()))
                .toList();
        List<Field> rightPhones = rightFields.stream().filter(f -> CONTACT_PHONE_TYPE.equals(f.getFieldType()))
                .toList();

        for (Field leftName : leftNames) {
            for (Field rightName : rightNames) {
                if (isSamePerson(leftName, rightName)) {
                    // 姓名相同且电话极其相似即判定命中
                    for (Field leftPhone : leftPhones) {
                        for (Field rightPhone : rightPhones) {
                            if (isNearbyPhone(leftPhone, rightPhone)) {
                                return buildHit(scope, leftName, leftPhone, rightName, rightPhone);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 判断是否为相同姓名。
     */
    private boolean isSamePerson(Field f1, Field f2) {
        String n1 = normalizeText(f1.getNormalizedValue());
        String n2 = normalizeText(f2.getNormalizedValue());
        return n1 != null && n1.equals(n2);
    }

    /**
     * 判断电话号码是否属于“近邻”范畴（尾数差异极小）。
     */
    private boolean isNearbyPhone(Field f1, Field f2) {
        String p1 = normalizePhone(f1.getNormalizedValue());
        String p2 = normalizePhone(f2.getNormalizedValue());
        if (p1 == null || p2 == null || p1.length() < 7 || p2.length() < 7) {
            return false;
        }
        if (p1.equals(p2)) {
            return true; // 完全相同直接判定
        }

        // 仅在长度一致时检查末尾字符差异
        if (p1.length() == p2.length()) {
            int diffCount = 0;
            int lastDiffPos = -1;
            for (int i = 0; i < p1.length(); i++) {
                if (p1.charAt(i) != p2.charAt(i)) {
                    diffCount++;
                    lastDiffPos = i;
                }
            }
            // 差异字符在 2 位以内，且均位于末尾部分
            return diffCount > 0 && diffCount <= 2 && lastDiffPos >= p1.length() - 2;
        }
        return false;
    }

    private String normalizeText(String text) {
        return text == null ? null : text.trim();
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceAll("[^0-9]", "");
    }

    /**
     * 构建命中项风险详情。
     */
    private RuleHit buildHit(CompareScope scope, Field leftName, Field leftPhone, Field rightName, Field rightPhone) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(98);

        hit.setMatchedValue(String.format("person:%s, phone1:%s, phone2:%s",
                leftName.getNormalizedValue(), leftPhone.getNormalizedValue(), rightPhone.getNormalizedValue()));

        hit.setTriggerSummary(String.format("文档 %s 与 %s 的联系人姓名均为“%s”，且其留存电话“%s”与“%s”极其相似，判定为关联方关系（联系方式近邻）。",
                leftName.getDocumentId(), rightName.getDocumentId(), leftName.getNormalizedValue(),
                leftPhone.getNormalizedValue(), rightPhone.getNormalizedValue()));

        hit.setDocumentIds(List.of(leftName.getDocumentId(), rightName.getDocumentId()));
        hit.setFieldIds(List.of(leftName.getFieldId(), leftPhone.getFieldId(), rightName.getFieldId(),
                rightPhone.getFieldId()));
        hit.setBlockIds(
                List.of(leftName.getBlockId(), leftPhone.getBlockId(), rightName.getBlockId(), rightPhone.getBlockId())
                        .stream().filter(Objects::nonNull).distinct().toList());

        List<RuleEvidence> evidences = new ArrayList<>();
        evidences.add(toEvidence(leftName));
        evidences.add(toEvidence(leftPhone));
        evidences.add(toEvidence(rightName));
        evidences.add(toEvidence(rightPhone));
        hit.setEvidences(evidences);

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
