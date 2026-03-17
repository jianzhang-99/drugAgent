package com.liang.drugagent.executor.tenderreview;

import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 版式模板同源规则执行器。
 *
 * <p>当前版本聚焦 W-M4：
 * 在同一比对范围内，如果两份投标文件的章节目录结构（标题内容及层级）高度一致，
 * 则判定为“版式模板同源”。</p>
 *
 * <p>约定上游将文档大纲/目录项抽取为 `fieldType=heading` 的字段：
 * `normalizedKey` 标识标题级别和序号（如 "H1:1."），
 * `normalizedValue` 存放标题文本（如 "1. 总体理解与建设目标"）。</p>
 *
 * @author liangjiajian
 */
@Component
public class TemplateHomologyExecutor implements TenderRuleExecutor {

    /** 标题字段类型。 */
    private static final String HEADING_FIELD_TYPE = "heading";
    /** 规则编码。 */
    private static final String RULE_CODE = "W-M4";
    /** 规则名称。 */
    private static final String RULE_NAME = "版式模板同源";
    /** 风险类型。 */
    private static final String RISK_TYPE = "collusion";
    /** 规则优先级。 */
    private static final String PRIORITY = "MEDIUM";
    /** 规则版本。 */
    private static final String VERSION = "v1";

    /** 判定同源的最少公共标题数。 */
    private static final int MIN_MATCHING_HEADINGS = 5;
    /** 相似度占比阈值。 */
    private static final BigDecimal SIMILARITY_THRESHOLD = new BigDecimal("0.80");

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

        Map<String, List<Field>> headingsByDocument = fields.stream()
                .filter(Objects::nonNull)
                .filter(field -> HEADING_FIELD_TYPE.equals(field.getFieldType()))
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();
        for (int i = 0; i < documentIds.size(); i++) {
            for (int j = i + 1; j < documentIds.size(); j++) {
                String leftDocId = documentIds.get(i);
                String rightDocId = documentIds.get(j);
                List<Field> leftFields = headingsByDocument.getOrDefault(leftDocId, List.of());
                List<Field> rightFields = headingsByDocument.getOrDefault(rightDocId, List.of());

                RuleHit hit = detectBetweenDocuments(scope, leftDocId, leftFields, rightDocId, rightFields);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    private RuleHit detectBetweenDocuments(CompareScope scope, String leftDocId, List<Field> leftFields,
                                           String rightDocId, List<Field> rightFields) {
        if (leftFields.isEmpty() || rightFields.isEmpty()) {
            return null;
        }

        // 提取标题文本集合用于比较（这里简化处理，认为文本完全一致即为匹配）
        List<String> leftHeadings = leftFields.stream().map(Field::getNormalizedValue).map(String::trim).toList();
        List<String> rightHeadings = rightFields.stream().map(Field::getNormalizedValue).map(String::trim).toList();

        // 计算匹配的标题数 (交集大小，假设标题文本具有一定的区分度)
        long matchCount = leftHeadings.stream().filter(rightHeadings::contains).count();
        int totalItems = Math.max(leftHeadings.size(), rightHeadings.size());

        if (totalItems == 0) return null;

        BigDecimal similarity = BigDecimal.valueOf(matchCount)
                .divide(BigDecimal.valueOf(totalItems), 2, RoundingMode.HALF_UP);

        if (matchCount >= MIN_MATCHING_HEADINGS && similarity.compareTo(SIMILARITY_THRESHOLD) >= 0) {
            return buildHit(scope, leftDocId, rightDocId, matchCount, similarity, leftFields, rightFields);
        }

        return null;
    }

    private RuleHit buildHit(CompareScope scope, String leftDocId, String rightDocId, long matchCount,
                              BigDecimal similarity, List<Field> leftFields, List<Field> rightFields) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(70); // 模板同源通常作为辅助证据，权重设为 70
        hit.setMatchedValue("matching_headings:" + matchCount + ", similarity:" + similarity);
        hit.setTriggerSummary(String.format("文档 %s 与 %s 的目录结构高度重合。共发现 %d 处相同的章节标题，结构相似度达 %s%%，疑似使用同一套投标模板。",
                leftDocId, rightDocId, matchCount, similarity.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString()));

        hit.setDocumentIds(List.of(leftDocId, rightDocId));
        
        // 收集匹配的字段 ID 作为证据链接
        List<String> matchFieldIds = new ArrayList<>();
        List<RuleEvidence> evidences = new ArrayList<>();

        // 这里只选前 10 个作为证据展示，避免列表太长
        int count = 0;
        for (Field left : leftFields) {
            for (Field right : rightFields) {
                if (Objects.equals(left.getNormalizedValue(), right.getNormalizedValue())) {
                    matchFieldIds.add(left.getFieldId());
                    matchFieldIds.add(right.getFieldId());
                    if (count < 10) {
                        evidences.add(toEvidence(left));
                        count++;
                    }
                    break;
                }
            }
        }

        hit.setFieldIds(matchFieldIds.stream().distinct().toList());
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
