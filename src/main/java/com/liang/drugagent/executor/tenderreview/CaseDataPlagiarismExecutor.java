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
import java.util.regex.Pattern;

/**
 * 案例数据抄袭执行器 (W-P6)。
 *
 * <p>
 * 识别不同投标文件的案例描述中特定的关键业务数据（如工厂数量、供应商数量、覆盖省份数等）是否完全对齐。
 * 旨在发现通过修改厂商名称但保留具体业务指标的案例包装行为。
 * </p>
 *
 * @author liangjiajian
 */
@Component
public class CaseDataPlagiarismExecutor implements TenderRuleExecutor {

    /** 案例数据字段类型。 */
    private static final String FIELD_TYPE = "case_data";
    /** 规则编码。 */
    private static final String RULE_CODE = "W-P6";
    /** 规则名称。 */
    private static final String RULE_NAME = "案例数据抄袭";
    /** 风险类型。 */
    private static final String RISK_TYPE = "plagiarism";
    /** 规则优先级。 */
    private static final String PRIORITY = "MEDIUM_HIGH";
    /** 规则版本。 */
    private static final String VERSION = "v1";

    /** 特定业务指标的正则匹配模式 (如: 8家、600余家、15个省、3个中心)。 */
    private static final Pattern METRIC_PATTERN = Pattern.compile(".*\\d+[家座个省中心].*");

    /**
     * 执行案例数据相似性检测。
     *
     * @param data 标书审查结构化输入数据
     * @return 命中命中案例数据命中的结果集
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
     * 在指定比对范围内检测高度同源的案例业务数据。
     */
    private List<RuleHit> detectInScope(CompareScope scope, List<Field> fields) {
        if (scope == null || scope.getDocumentIds() == null || scope.getDocumentIds().size() < 2) {
            return List.of();
        }

        // 提取并聚合案例数据类字段
        Map<String, List<Field>> fieldsByDoc = fields.stream()
                .filter(Objects::nonNull)
                .filter(field -> FIELD_TYPE.equals(field.getFieldType()))
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();

        // 执行文档间的交叉比对
        for (int i = 0; i < documentIds.size(); i++) {
            for (int j = i + 1; j < documentIds.size(); j++) {
                String leftId = documentIds.get(i);
                String rightId = documentIds.get(j);
                List<Field> leftFields = fieldsByDoc.getOrDefault(leftId, List.of());
                List<Field> rightFields = fieldsByDoc.getOrDefault(rightId, List.of());

                hits.addAll(compareCaseData(scope, leftFields, rightFields));
            }
        }
        return hits;
    }

    /**
     * 对比两份文档中符合特定指标模式的案例数据。
     */
    private List<RuleHit> compareCaseData(CompareScope scope, List<Field> left, List<Field> right) {
        List<RuleHit> hits = new ArrayList<>();
        for (Field l : left) {
            for (Field r : right) {
                String v1 = l.getNormalizedValue();
                String v2 = r.getNormalizedValue();

                // 仅比对完全一致且包含特定量词指标的案例数值
                if (v1 != null && v1.equals(v2) && METRIC_PATTERN.matcher(v1).matches()) {
                    hits.add(buildHit(scope, l, r));
                }
            }
        }
        return hits;
    }

    /**
     * 构建案例数据风险命中详情。
     */
    private RuleHit buildHit(CompareScope scope, Field left, Field right) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(90);
        hit.setMatchedValue(left.getNormalizedValue());

        hit.setTriggerSummary(String.format("文档 %s 与 %s 均在同类案例中声称具备“%s”的业务规模。特定的颗粒度数据完全一致，存在严重的案例洗稿或包装风险。",
                left.getDocumentId(), right.getDocumentId(), left.getNormalizedValue()));

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
