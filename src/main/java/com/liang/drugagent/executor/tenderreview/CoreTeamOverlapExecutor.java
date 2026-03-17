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
 * 核心团队重叠执行器 (W-M3)。
 *
 * <p>
 * 识别不同投标主体的核心团队人员（如项目经理、架构师等）姓名及其简历描述是否实质性重合。
 * 旨在通过人员信息的特征匹配来识别潜在的关联围标或陪标行为。
 * </p>
 *
 * @author liangjiajian
 */
@Component
public class CoreTeamOverlapExecutor implements TenderRuleExecutor {

    /** 团队成员字段类型。 */
    private static final String TEAM_MEMBER_FIELD_TYPE = "team_member";
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

    /** 判定简历实质重合的相似度阈值系数。 */
    private static final double SIMILARITY_THRESHOLD = 0.95;

    /**
     * 执行核心团队重叠识别。
     *
     * @param data 标书审查结构化输入数据
     * @return 命中人员信息命中的结果集
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
     * 在指定比对范围内检测重合的团队成员。
     */
    private List<RuleHit> detectInScope(CompareScope scope, List<Field> fields) {
        if (scope == null || scope.getDocumentIds() == null || scope.getDocumentIds().size() < 2) {
            return List.of();
        }

        // 按文档归集人员相关字段
        Map<String, List<Field>> teamFieldsByDoc = fields.stream()
                .filter(Objects::nonNull)
                .filter(field -> TEAM_MEMBER_FIELD_TYPE.equals(field.getFieldType()))
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();

        // 两两比对不同厂商文档中出现的人员
        for (int i = 0; i < documentIds.size(); i++) {
            for (int j = i + 1; j < documentIds.size(); j++) {
                String leftDocId = documentIds.get(i);
                String rightDocId = documentIds.get(j);
                List<Field> leftFields = teamFieldsByDoc.getOrDefault(leftDocId, List.of());
                List<Field> rightFields = teamFieldsByDoc.getOrDefault(rightDocId, List.of());

                for (Field left : leftFields) {
                    for (Field right : rightFields) {
                        if (isSameMember(left, right)) {
                            hits.add(buildHit(scope, left, right));
                        }
                    }
                }
            }
        }
        return hits;
    }

    /**
     * 判断是否为同一人且简历基本一致。
     */
    private boolean isSameMember(Field f1, Field f2) {
        // normalizedKey 存放姓名，normalizedValue 存放详细简历描述
        String name1 = f1.getNormalizedKey();
        String name2 = f2.getNormalizedKey();

        if (name1 == null || name2 == null || !name1.trim().equals(name2.trim())) {
            return false;
        }

        // 姓名匹配成功后，执行简历内容相似度精细比对
        String resume1 = f1.getNormalizedValue();
        String resume2 = f2.getNormalizedValue();

        return calculateSimilarity(resume1, resume2) >= SIMILARITY_THRESHOLD;
    }

    /**
     * 计算两个简历文本字符串的相似度。
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
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
     * Levenshtein 编辑距离算法。
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
     * 构建团队重叠项风险命中详情。
     */
    private RuleHit buildHit(CompareScope scope, Field left, Field right) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(95);

        double sim = calculateSimilarity(left.getNormalizedValue(), right.getNormalizedValue());
        hit.setMatchedValue("member_name:" + left.getNormalizedKey());

        hit.setTriggerSummary(String.format("文档 %s 与 %s 的核心团队成员“%s”完全重叠。两份简历描述的高度匹配（相似度 %.0f%%），存在串通投标风风险。",
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
