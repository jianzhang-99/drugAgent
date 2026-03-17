package com.liang.drugagent.executor.tenderreview;

import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.Field;
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
public class ErrorReplicationExecutor extends AbstractTenderExecutor {

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

    /** 预定义的低频/罕见错别字库。 */
    private static final List<String> RARE_ERRORS = List.of("应急响映", "方案设记", "技术架勾");

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

        List<RuleHit> hits = new ArrayList<>();
        for (String error : RARE_ERRORS) {
            // 筛选出包含该罕见错误的所有字段并按文档分组
            Map<String, List<Field>> byDoc = fields.stream()
                    .filter(Objects::nonNull)
                    .filter(f -> scope.getDocumentIds().contains(f.getDocumentId()))
                    .filter(f -> f.getNormalizedValue() != null && f.getNormalizedValue().contains(error))
                    .collect(Collectors.groupingBy(Field::getDocumentId));

            if (byDoc.size() >= 2) {
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

    private RuleHit buildHit(CompareScope scope, String error, Field left, Field right) {
        RuleHit hit = createBaseHit(RULE_CODE, RULE_NAME, scope.getScopeId(), RISK_TYPE, PRIORITY, VERSION);
        hit.setWeight(100);
        hit.setMatchedValue(error);

        hit.setTriggerSummary(String.format("文档 %s 与 %s 同步出现了低频/罕见错别字“%s”。正常创作下犯完全相同错误的概率极低，判定为高度同源复现。",
                left.getDocumentId(), right.getDocumentId(), error));

        hit.setDocumentIds(List.of(left.getDocumentId(), right.getDocumentId()));
        hit.setFieldIds(List.of(left.getFieldId(), right.getFieldId()));
        hit.setBlockIds(List.of(left.getBlockId(), right.getBlockId()).stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList()));

        hit.setEvidences(List.of(toEvidence(left), toEvidence(right)));
        return hit;
    }
}
