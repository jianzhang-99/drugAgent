package com.liang.drugagent.executor;

import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.executor.tenderreview.QuoteGradientExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报价梯度异常规则执行器单元测试。
 *
 * @author liangjiajian
 */
class QuoteGradientExecutorTest {

    private final QuoteGradientExecutor executor = new QuoteGradientExecutor();

    /**
     * 验证存在固定报价梯度时可以命中 W-M1。
     */
    @Test
    void shouldHitWhenMultipleQuoteItemsShareSameDelta() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-QUOTE-001");
        scope.setScopeType("full_bid_compare");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        data.setFields(List.of(
                quoteField("F-A-1", "DOC-A", "迁移服务", "100000"),
                quoteField("F-B-1", "DOC-B", "迁移服务", "102000"),
                quoteField("F-A-2", "DOC-A", "存储服务", "200000"),
                quoteField("F-B-2", "DOC-B", "存储服务", "202000"),
                quoteField("F-A-3", "DOC-A", "运维服务", "300000"),
                quoteField("F-B-3", "DOC-B", "运维服务", "302000"),
                quoteField("F-A-4", "DOC-A", "辅材", "5000"),
                quoteField("F-B-4", "DOC-B", "辅材", "4000")
        ));

        RuleResult result = executor.execute(data);

        assertEquals(1, result.getHits().size());
        RuleHit hit = result.getHits().get(0);
        assertEquals("W-M1", hit.getRuleCode());
        assertEquals("报价梯度异常", hit.getRuleName());
        assertTrue(hit.getTriggerSummary().contains("固定价差 2000"));
        assertTrue(hit.getTriggerSummary().contains("迁移服务"));
        assertNotNull(hit.getEvidences());
        assertEquals(6, hit.getEvidences().size());
    }

    /**
     * 验证报价差额无明显规律时不会误判。
     */
    @Test
    void shouldIgnoreWhenNoDominantDeltaPatternExists() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-QUOTE-002");
        scope.setScopeType("full_bid_compare");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        data.setFields(List.of(
                quoteField("F-A-1", "DOC-A", "迁移服务", "100000"),
                quoteField("F-B-1", "DOC-B", "迁移服务", "103000"),
                quoteField("F-A-2", "DOC-A", "存储服务", "200000"),
                quoteField("F-B-2", "DOC-B", "存储服务", "206000"),
                quoteField("F-A-3", "DOC-A", "运维服务", "300000"),
                quoteField("F-B-3", "DOC-B", "运维服务", "301500")
        ));

        RuleResult result = executor.execute(data);

        assertTrue(result.getHits().isEmpty());
    }

    /**
     * 构造报价字段。
     *
     * @param fieldId 字段 ID
     * @param documentId 文档 ID
     * @param itemName 报价项名称
     * @param amount 金额
     * @return 报价字段
     */
    private Field quoteField(String fieldId, String documentId, String itemName, String amount) {
        Field field = new Field();
        field.setFieldId(fieldId);
        field.setDocumentId(documentId);
        field.setBlockId("BLK-" + fieldId);
        field.setFieldType("quote_item");
        field.setFieldName(itemName);
        field.setFieldValue(amount);
        field.setNormalizedValue(amount);
        field.setNormalizedKey("quote_item:" + itemName);
        field.setChapterPath("第六章/报价明细");
        return field;
    }
}
