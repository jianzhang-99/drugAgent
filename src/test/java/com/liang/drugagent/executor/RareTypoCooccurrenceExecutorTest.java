package com.liang.drugagent.executor;

import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.executor.tenderreview.RareTypoCooccurrenceExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 罕见错误共现规则执行器单元测试。
 *
 * @author liangjiajian
 */
class RareTypoCooccurrenceExecutorTest {

    private final RareTypoCooccurrenceExecutor executor = new RareTypoCooccurrenceExecutor();

    /**
     * 验证当两份标书共同出现相同的错别字时命中 W-M5。
     */
    @Test
    void shouldHitWhenRareTypoCooccurs() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-TYPO-001");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        data.setFields(List.of(
                typoField("FA1", "DOC-A", "应急响映"),
                typoField("FB1", "DOC-B", "应急响映"),
                typoField("FA2", "DOC-A", "系统崩溃"), // 正常词或非共现 typo
                typoField("FC1", "DOC-C", "应急响映")  // 不在比对范围内
        ));

        RuleResult result = executor.execute(data);

        assertEquals(1, result.getHits().size());
        RuleHit hit = result.getHits().get(0);
        assertEquals("W-M5", hit.getRuleCode());
        assertTrue(hit.getTriggerSummary().contains("应急响映"));
        assertEquals(2, hit.getDocumentIds().size());
        assertEquals(2, hit.getEvidences().size());
    }

    /**
     * 验证不同错别字不会触发。
     */
    @Test
    void shouldNotHitWhenTyposAreDifferent() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-TYPO-002");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        data.setFields(List.of(
                typoField("FA1", "DOC-A", "应急响映"),
                typoField("FB1", "DOC-B", "应急晌应") // 细微差别也不算完全共现
        ));

        RuleResult result = executor.execute(data);

        assertTrue(result.getHits().isEmpty());
    }

    private Field typoField(String fieldId, String documentId, String typoText) {
        Field field = new Field();
        field.setFieldId(fieldId);
        field.setDocumentId(documentId);
        field.setFieldType("typo");
        field.setNormalizedValue(typoText);
        field.setNormalizedKey("typo_key");
        field.setChapterPath("第五章/应急预案");
        return field;
    }
}
