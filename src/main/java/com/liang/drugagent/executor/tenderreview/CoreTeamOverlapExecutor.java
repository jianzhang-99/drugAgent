package com.liang.drugagent.executor.tenderreview;

import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 核心团队重叠规则执行器。
 *
 * <p>当前版本聚焦 W-M3：
 * 如果两份投标文件中存在多个核心岗位成员姓名一致，且简历摘要完全一致，
 * 则判定为“核心团队重叠”。</p>
 *
 * <p>当前约定上游将团队成员信息抽取为 `fieldType=team_member` 或 `fieldType=core_team_member`：
 * `fieldName` 存放岗位名称，
 * `normalizedKey` 存放成员姓名，
 * `normalizedValue` 存放标准化后的简历摘要。</p>
 *
 * @author liangjiajian
 */
@Component
public class CoreTeamOverlapExecutor implements TenderRuleExecutor {

    /** 规则编码。 */
    private static final String RULE_CODE = "W-M3";
    /** 规则名称。 */
    private static final String RULE_NAME = "核心团队重叠";
    /** 风险类型。 */
    private static final String RISK_TYPE = "collusion";
    /** 规则优先级。 */
    private static final String PRIORITY = "HIGH";
    /** 规则版本。 */
    private static final String VERSION = "v1";
    /** 最少重叠人数阈值。 */
    private static final int MIN_OVERLAP_COUNT = 2;

    /** 核心岗位关键字。 */
    private static final List<String> CORE_ROLE_KEYWORDS = List.of(
            "项目经理",
            "架构师",
            "系统架构师",
            "技术架构师",
            "工程师"
    );

    /**
     * 执行核心团队重叠识别。
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

        Map<String, List<Field>> teamFieldsByDocument = fields.stream()
                .filter(Objects::nonNull)
                .filter(this::isSupportedTeamField)
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId, LinkedHashMap::new, Collectors.toList()));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();
        for (int i = 0; i < documentIds.size(); i++) {
            for (int j = i + 1; j < documentIds.size(); j++) {
                String leftDocumentId = documentIds.get(i);
                String rightDocumentId = documentIds.get(j);
                RuleHit hit = detectBetweenDocuments(
                        scope,
                        leftDocumentId,
                        teamFieldsByDocument.getOrDefault(leftDocumentId, List.of()),
                        rightDocumentId,
                        teamFieldsByDocument.getOrDefault(rightDocumentId, List.of())
                );
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    private RuleHit detectBetweenDocuments(CompareScope scope, String leftDocumentId, List<Field> leftFields,
                                           String rightDocumentId, List<Field> rightFields) {
        Map<String, Field> leftMemberMap = toMemberMap(leftFields);
        Map<String, Field> rightMemberMap = toMemberMap(rightFields);

        Set<String> commonMembers = new LinkedHashSet<>(leftMemberMap.keySet());
        commonMembers.retainAll(rightMemberMap.keySet());
        if (commonMembers.isEmpty()) {
            return null;
        }

        List<MemberOverlap> overlaps = new ArrayList<>();
        for (String memberKey : commonMembers) {
            Field leftField = leftMemberMap.get(memberKey);
            Field rightField = rightMemberMap.get(memberKey);
            if (!resumeSame(leftField, rightField)) {
                continue;
            }
            overlaps.add(new MemberOverlap(memberKey, leftField, rightField));
        }

        if (overlaps.size() < MIN_OVERLAP_COUNT) {
            return null;
        }

        return buildHit(scope, leftDocumentId, rightDocumentId, overlaps);
    }

    private boolean isSupportedTeamField(Field field) {
        if (field == null) {
            return false;
        }
        String fieldType = field.getFieldType();
        if (!"team_member".equals(fieldType) && !"core_team_member".equals(fieldType)) {
            return false;
        }
        if (field.getFieldName() == null || field.getNormalizedKey() == null) {
            return false;
        }
        return isCoreRole(field.getFieldName());
    }

    private boolean isCoreRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        return CORE_ROLE_KEYWORDS.stream().anyMatch(role::contains);
    }

    private Map<String, Field> toMemberMap(List<Field> teamFields) {
        Map<String, Field> memberMap = new LinkedHashMap<>();
        for (Field field : teamFields) {
            String personName = normalizeText(field.getNormalizedKey());
            if (personName == null) {
                continue;
            }
            memberMap.putIfAbsent(personName, field);
        }
        return memberMap;
    }

    private boolean resumeSame(Field leftField, Field rightField) {
        String leftResume = normalizeText(firstNonBlank(leftField.getNormalizedValue(), leftField.getFieldValue()));
        String rightResume = normalizeText(firstNonBlank(rightField.getNormalizedValue(), rightField.getFieldValue()));
        return leftResume != null && leftResume.equals(rightResume);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", "");
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private RuleHit buildHit(CompareScope scope, String leftDocumentId, String rightDocumentId, List<MemberOverlap> overlaps) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(95);
        hit.setMatchedValue(buildMatchedValue(overlaps));
        hit.setTriggerSummary(buildTriggerSummary(leftDocumentId, rightDocumentId, overlaps));
        hit.setDocumentIds(List.of(leftDocumentId, rightDocumentId));
        hit.setFieldIds(overlaps.stream()
                .flatMap(overlap -> java.util.stream.Stream.of(overlap.leftField().getFieldId(), overlap.rightField().getFieldId()))
                .filter(Objects::nonNull)
                .toList());
        hit.setBlockIds(overlaps.stream()
                .flatMap(overlap -> java.util.stream.Stream.of(overlap.leftField().getBlockId(), overlap.rightField().getBlockId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        hit.setEvidences(buildEvidences(overlaps));
        hit.setVersion(VERSION);
        return hit;
    }

    private String buildMatchedValue(List<MemberOverlap> overlaps) {
        return overlaps.stream()
                .map(overlap -> overlap.memberName() + "/" + overlap.leftField().getFieldName())
                .collect(Collectors.joining("、"));
    }

    private String buildTriggerSummary(String leftDocumentId, String rightDocumentId, List<MemberOverlap> overlaps) {
        String members = overlaps.stream()
                .map(overlap -> overlap.memberName() + "（" + overlap.leftField().getFieldName() + "）")
                .collect(Collectors.joining("、"));
        return "文档 " + leftDocumentId + " 与 " + rightDocumentId
                + " 存在 " + overlaps.size() + " 名核心成员姓名及简历摘要完全一致，重叠成员包括：" + members;
    }

    private List<RuleEvidence> buildEvidences(List<MemberOverlap> overlaps) {
        List<RuleEvidence> evidences = new ArrayList<>();
        for (MemberOverlap overlap : overlaps) {
            evidences.add(toEvidence(overlap.leftField()));
            evidences.add(toEvidence(overlap.rightField()));
        }
        return evidences;
    }

    private RuleEvidence toEvidence(Field field) {
        RuleEvidence evidence = new RuleEvidence();
        evidence.setDocumentId(field.getDocumentId());
        evidence.setFieldId(field.getFieldId());
        evidence.setBlockId(field.getBlockId());
        evidence.setMatchedValue(field.getNormalizedKey() + " / " + field.getFieldName() + " / "
                + firstNonBlank(field.getNormalizedValue(), field.getFieldValue()));
        evidence.setChapterPath(field.getChapterPath());
        evidence.setAnchor(field.getAnchor());
        return evidence;
    }

    /**
     * 核心成员重叠明细。
     *
     * @param memberName 成员姓名
     * @param leftField 左文档成员字段
     * @param rightField 右文档成员字段
     */
    private record MemberOverlap(String memberName, Field leftField, Field rightField) {
    }
}
