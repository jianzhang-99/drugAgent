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
public class CoreTeamOverlapExecutor extends AbstractTenderExecutor {

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
     * 在特定的对比范围内执行风险探测。
     * 通常是一个标段（CompareScope）内的多个文档。
     *
     * @param scope 对比范围
     * @param fields 已提取的所有字段
     * @return 命中项列表
     */
    private List<RuleHit> detectInScope(CompareScope scope, List<Field> fields) {
        if (scope == null || scope.getDocumentIds() == null || scope.getDocumentIds().size() < 2) {
            return List.of();
        }

        Map<String, List<Field>> teamFieldsByDoc = fields.stream()
                .filter(Objects::nonNull)
                .filter(field -> TEAM_MEMBER_FIELD_TYPE.equals(field.getFieldType()))
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();

        for (int i = 0; i < documentIds.size(); i++) {
            String leftDocId = documentIds.get(i);
            List<Field> leftFields = teamFieldsByDoc.getOrDefault(leftDocId, List.of());
            if (leftFields.isEmpty())
                continue;

            for (int j = i + 1; j < documentIds.size(); j++) {
                String rightDocId = documentIds.get(j);
                List<Field> rightFields = teamFieldsByDoc.getOrDefault(rightDocId, List.of());
                if (rightFields.isEmpty())
                    continue;

                List<Field[]> matches = new ArrayList<>();
                for (Field left : leftFields) {
                    for (Field right : rightFields) {
                        double similarity = getSimilarity(left, right);
                        if (similarity >= SIMILARITY_THRESHOLD) {
                            matches.add(new Field[]{left, right});
                        }
                    }
                }

                if (!matches.isEmpty()) {
                    hits.add(buildAggregatedHit(scope, leftDocId, rightDocId, matches));
                }
            }
        }
        return hits;
    }

    /**
     * 构建聚合的命中记录对象。
     */
    private RuleHit buildAggregatedHit(CompareScope scope, String leftDocId, String rightDocId, List<Field[]> matches) {
        RuleHit hit = createBaseHit(RULE_CODE, RULE_NAME, scope.getScopeId(), RISK_TYPE, PRIORITY, VERSION);
        hit.setWeight(95);

        List<String> names = matches.stream()
                .map(m -> m[0].getNormalizedKey())
                .distinct()
                .collect(Collectors.toList());
        hit.setMatchedValue("overlapping_members:" + String.join(",", names));

        String namesSummary = String.join("、", names);
        hit.setTriggerSummary(String.format("文档 %s 与 %s 的核心团队成员存在严重重叠（涉及 %d 人：%s）。两投主体的简历描述高度匹配，存在串通投标风险。",
                leftDocId, rightDocId, names.size(), namesSummary));

        hit.setDocumentIds(List.of(leftDocId, rightDocId));

        List<String> fieldIds = new ArrayList<>();
        List<String> blockIds = new ArrayList<>();
        List<RuleEvidence> evidences = new ArrayList<>();

        for (Field[] pair : matches) {
            fieldIds.add(pair[0].getFieldId());
            fieldIds.add(pair[1].getFieldId());
            blockIds.add(pair[0].getBlockId());
            blockIds.add(pair[1].getBlockId());
            evidences.add(toEvidence(pair[0]));
            evidences.add(toEvidence(pair[1]));
        }

        hit.setFieldIds(fieldIds.stream().distinct().collect(Collectors.toList()));
        hit.setBlockIds(blockIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        hit.setEvidences(evidences);
        return hit;
    }

    /**
     * 计算两个字段（成员信息）之间的相似度。
     * 首先匹配成员姓名（NormalizedKey），如果姓名一致，则计算简历（NormalizedValue）的文本相似度。
     *
     * @param f1 成员字段1
     * @param f2 成员字段2
     * @return 相似度得分 (0.0 - 1.0)
     */
    private double getSimilarity(Field f1, Field f2) {
        String name1 = f1.getNormalizedKey();
        String name2 = f2.getNormalizedKey();
        if (name1 == null || name2 == null || !name1.trim().equals(name2.trim())) {
            return 0.0;
        }

        String resume1 = f1.getNormalizedValue();
        String resume2 = f2.getNormalizedValue();
        if (resume1 == null || resume2 == null)
            return 0.0;
        if (resume1.equals(resume2))
            return 1.0;

        int longerLength = Math.max(resume1.length(), resume2.length());
        if (longerLength == 0)
            return 1.0;
        return (longerLength - editDistance(resume1, resume2)) / (double) longerLength;
    }

    /**
     * 使用编辑距离算法（Levenshtein Distance）计算两个字符串的差异。
     * 空间优化的实现，只需一行数组。
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

}
