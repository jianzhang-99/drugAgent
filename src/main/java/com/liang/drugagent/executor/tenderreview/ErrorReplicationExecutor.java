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
 * 错误复现抄袭执行器 (W-P5)。
 *
 * <p>
 * 识别不同比对范围内的标书文档中是否同步出现了罕见的、低频的错别字或排版错误。
 * 由于正常情况下不同厂商不太可能犯完全相同的稀有错误，此类现象被定性为极高风险的抄袭证据。
 * </p>
 *
 * @author liangjiajian
 */
@Component
public class ErrorReplicationExecutor implements TenderRuleExecutor {

    /** 规则编码。 */
    private static final String RULE_CODE = "W-P5";
    /** 规则名称。 */
    private static final String RULE_NAME = "错误复现抄袭";
    /** 风险类型。 */
    private static final String RISK_TYPE = "plagiarism";
    /** 规则优先级。 */
    private static final String PRIORITY = "CRITICAL";
    /** 规则版本。 */
    private static final String VERSION = "v1";

    /**
     * 预定义的低频/罕见错别字库。
     * 实际应用中可扩展为动态配置或外部字典。
     */
    private static final List<String> RARE_ERRORS = List.of("应急响映", "方案设记", "技术架勾");

    /**
     * 执行错误复现识别。
     *
     * @param data 标书审查结构化输入数据
     * @return 包含命中罕见错误的结果集
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
     * 在比对范围内针对每个预定义的罕见错误进行扫描。
     */
    private List<RuleHit> detectInScope(CompareScope scope, List<Field> fields) {
        if (scope == null || scope.getDocumentIds() == null || scope.getDocumentIds().size() < 2) {
            return List.of();
        }

        List<RuleHit> hits = new ArrayList<>();

        for (String error : RARE_ERRORS) {
            // 筛选出包含该罕见错误的所有字段
            List<Field> fieldsWithError = fields.stream()
                    .filter(Objects::nonNull)
                    .filter(f -> scope.getDocumentIds().contains(f.getDocumentId()))
                    .filter(f -> f.getNormalizedValue() != null && f.getNormalizedValue().contains(error))
                    .toList();

            // 按文档 ID 分组，统计有多少个独立文档犯了同样的错误
            Map<String, List<Field>> byDoc = fieldsWithError.stream()
                    .collect(Collectors.groupingBy(Field::getDocumentId));

            if (byDoc.size() >= 2) {
                // 如果超过2个文档出现同一错误，进行两两组对命中
                List<String> docs = new ArrayList<>(byDoc.keySet());
                for (int i = 0; i < docs.size(); i++) {
                    for (int j = i + 1; j < docs.size(); j++) {
                        hits.add(buildHit(scope, error, byDoc.get(docs.get(i)).get(0), byDoc.get(docs.get(j)).get(0)));
                    }
                }
            }
        }
        return hits;
    }

    /**
     * 构建罕见错误命中的风险详情。
     */
    private RuleHit buildHit(CompareScope scope, String error, Field left, Field right) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(RULE_CODE);
        hit.setRuleName(RULE_NAME);
        hit.setScopeId(scope.getScopeId());
        hit.setRiskType(RISK_TYPE);
        hit.setPriority(PRIORITY);
        hit.setWeight(100);
        hit.setMatchedValue(error);

        hit.setTriggerSummary(String.format("文档 %s 与 %s 同步出现了低频/罕见错别字“%s”。正常创作下犯完全相同错误的概率极低，判定为高度同源复现。",
                left.getDocumentId(), right.getDocumentId(), error));

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
