package com.liang.drugagent.executor.tenderreview;

import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.domain.tenderreview.CompareScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 报价梯度异常规则执行器。
 *
 * <p>
 * 当前版本聚焦 W-M1：
 * 在同一比对范围内，如果两份投标文件的多个相同报价项存在稳定的固定价差，
 * 则判定为“报价梯度异常”。
 * </p>
 *
 * <p>
 * 当前约定上游将报价明细抽取为 `fieldType=quote_item` 的字段：
 * `normalizedKey` 标识报价项，
 * `normalizedValue` 存放可解析的数值金额。
 * </p>
 *
 * @author liangjiajian
 */
@Component
public class QuoteGradientExecutor implements TenderRuleExecutor {

    /** 报价项字段类型。 */
    private static final String QUOTE_ITEM_FIELD_TYPE = "quote_item";
    /** 规则编码。 */
    private static final String RULE_CODE = "W-M1";
    /** 规则名称。 */
    private static final String RULE_NAME = "报价梯度异常";
    /** 风险类型。 */
    private static final String RISK_TYPE = "collusion";
    /** 规则优先级。 */
    private static final String PRIORITY = "HIGH";
    /** 规则版本。 */
    private static final String VERSION = "v1";
    /** 最少可比较报价项数。 */
    private static final int MIN_COMPARABLE_ITEMS = 3;
    /** 至少需要出现几次相同价差。 */
    private static final int MIN_REPEATED_DELTA_COUNT = 2;
    /** 主导价差占比阈值。 */
    private static final BigDecimal MIN_DOMINANT_RATIO = new BigDecimal("0.60");

    /**
     * 执行报价梯度异常识别。
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

        Map<String, List<Field>> quoteFieldsByDocument = fields.stream()
                .filter(Objects::nonNull)
                .filter(field -> QUOTE_ITEM_FIELD_TYPE.equals(field.getFieldType()))
                .filter(field -> scope.getDocumentIds().contains(field.getDocumentId()))
                .collect(Collectors.groupingBy(Field::getDocumentId));

        List<RuleHit> hits = new ArrayList<>();
        List<String> documentIds = scope.getDocumentIds();
        for (int i = 0; i < documentIds.size(); i++) {
            for (int j = i + 1; j < documentIds.size(); j++) {
                String leftDocumentId = documentIds.get(i);
                String rightDocumentId = documentIds.get(j);
                List<Field> leftFields = quoteFieldsByDocument.getOrDefault(leftDocumentId, List.of());
                List<Field> rightFields = quoteFieldsByDocument.getOrDefault(rightDocumentId, List.of());
                RuleHit hit = detectBetweenDocuments(scope, leftDocumentId, leftFields, rightDocumentId, rightFields);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    private RuleHit detectBetweenDocuments(CompareScope scope, String leftDocumentId, List<Field> leftFields,
            String rightDocumentId, List<Field> rightFields) {
        Map<String, Field> leftByKey = toQuoteMap(leftFields);
        Map<String, Field> rightByKey = toQuoteMap(rightFields);

        Set<String> commonKeys = new LinkedHashSet<>(leftByKey.keySet());
        commonKeys.retainAll(rightByKey.keySet());
        if (commonKeys.size() < MIN_COMPARABLE_ITEMS) {
            return null;
        }

        List<QuoteDelta> deltas = new ArrayList<>();
        for (String commonKey : commonKeys) {
            Field leftField = leftByKey.get(commonKey);
            Field rightField = rightByKey.get(commonKey);
            BigDecimal leftValue = parseAmount(leftField.getNormalizedValue());
            BigDecimal rightValue = parseAmount(rightField.getNormalizedValue());
            if (leftValue == null || rightValue == null) {
                continue;
            }
            BigDecimal delta = rightValue.subtract(leftValue);
            if (delta.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            deltas.add(new QuoteDelta(commonKey, delta, leftField, rightField));
        }

        if (deltas.size() < MIN_COMPARABLE_ITEMS) {
            return null;
        }

        Map<BigDecimal, List<QuoteDelta>> deltasByValue = new HashMap<>();
        for (QuoteDelta quoteDelta : deltas) {
            deltasByValue.computeIfAbsent(quoteDelta.delta(), key -> new ArrayList<>()).add(quoteDelta);
        }

        Map.Entry<BigDecimal, List<QuoteDelta>> dominantEntry = deltasByValue.entrySet().stream()
                .max(Comparator.comparingInt(entry -> entry.getValue().size()))
                .orElse(null);
        if (dominantEntry == null) {
            return null;
        }

        int dominantCount = dominantEntry.getValue().size();
        BigDecimal dominantRatio = BigDecimal.valueOf(dominantCount)
                .divide(BigDecimal.valueOf(deltas.size()), 2, RoundingMode.HALF_UP);
        if (dominantCount < MIN_REPEATED_DELTA_COUNT || dominantRatio.compareTo(MIN_DOMINANT_RATIO) < 0) {
            return null;
        }

        return buildHit(scope, leftDocumentId, rightDocumentId, dominantEntry.getKey(), dominantEntry.getValue(),
                deltas.size());
    }

    private Map<String, Field> toQuoteMap(List<Field> quoteFields) {
        Map<String, Field> quoteMap = new HashMap<>();
        for (Field field : quoteFields) {
            if (field.getNormalizedKey() == null || field.getNormalizedKey().isBlank()) {
                continue;
            }
            quoteMap.putIfAbsent(field.getNormalizedKey(), field);
        }
        return quoteMap;
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // 增强解析：过滤货币符号、逗号以及空格，确保解析成功率
            String cleanedValue = value.replaceAll("[￥$元,]", "").trim();
            if (cleanedValue.isEmpty()) {
                return null;
            }
            return new BigDecimal(cleanedValue);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private RuleHit buildHit(CompareScope scope, String leftDocumentId, String rightDocumentId,
            BigDecimal dominantDelta,
            List<QuoteDelta> dominantDeltas, int comparableCount) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(95);
        hit.setMatchedValue("dominant_delta:" + dominantDelta.stripTrailingZeros().toPlainString());
        hit.setTriggerSummary(
                buildTriggerSummary(leftDocumentId, rightDocumentId, dominantDelta, dominantDeltas, comparableCount));
        hit.setDocumentIds(List.of(leftDocumentId, rightDocumentId));
        hit.setFieldIds(dominantDeltas.stream()
                .flatMap(delta -> List.of(delta.leftField().getFieldId(), delta.rightField().getFieldId()).stream())
                .toList());
        hit.setBlockIds(dominantDeltas.stream()
                .flatMap(delta -> List.of(delta.leftField().getBlockId(), delta.rightField().getBlockId()).stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        hit.setEvidences(buildEvidences(dominantDeltas, dominantDelta));
        hit.setVersion(VERSION);
        return hit;
    }

    private String buildTriggerSummary(String leftDocumentId, String rightDocumentId, BigDecimal dominantDelta,
            List<QuoteDelta> dominantDeltas, int comparableCount) {
        String items = dominantDeltas.stream()
                .map(delta -> readableQuoteItem(delta.itemKey()))
                .collect(Collectors.joining("、"));
        return "文档 " + leftDocumentId + " 与 " + rightDocumentId
                + " 的 " + dominantDeltas.size() + " 个报价项存在固定价差 "
                + dominantDelta.stripTrailingZeros().toPlainString()
                + "，共比较 " + comparableCount + " 个报价项，异常项包括：" + items;
    }

    private List<RuleEvidence> buildEvidences(List<QuoteDelta> dominantDeltas, BigDecimal dominantDelta) {
        List<RuleEvidence> evidences = new ArrayList<>();
        for (QuoteDelta delta : dominantDeltas) {
            evidences.add(toEvidence(delta.leftField(), dominantDelta, delta.rightField().getNormalizedValue()));
            evidences.add(toEvidence(delta.rightField(), dominantDelta, delta.leftField().getNormalizedValue()));
        }
        return evidences;
    }

    private RuleEvidence toEvidence(Field field, BigDecimal dominantDelta, String comparedValue) {
        RuleEvidence evidence = new RuleEvidence();
        evidence.setDocumentId(field.getDocumentId());
        evidence.setFieldId(field.getFieldId());
        evidence.setBlockId(field.getBlockId());
        evidence.setMatchedValue(field.getNormalizedValue() + " (对比值 " + comparedValue
                + ", 固定差额 " + dominantDelta.stripTrailingZeros().toPlainString() + ")");
        evidence.setChapterPath(field.getChapterPath());
        evidence.setAnchor(field.getAnchor());
        return evidence;
    }

    private String readableQuoteItem(String itemKey) {
        int index = itemKey.indexOf(':');
        return index >= 0 ? itemKey.substring(index + 1) : itemKey;
    }

    /**
     * 报价项差额明细。
     *
     * @param itemKey    报价项键
     * @param delta      差额
     * @param leftField  左文档报价字段
     * @param rightField 右文档报价字段
     */
    private record QuoteDelta(String itemKey, BigDecimal delta, Field leftField, Field rightField) {
    }
}
