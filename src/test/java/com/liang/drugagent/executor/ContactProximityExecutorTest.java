package com.liang.drugagent.executor;

import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.executor.tenderreview.ContactProximityExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 联系方式近邻规则执行器单元测试。
 *
 * @author liangjiajian
 */
class ContactProximityExecutorTest {

    private final ContactProximityExecutor executor = new ContactProximityExecutor();

    /**
     * 验证姓名相同且电话号码末位不同时命中 W-M2。
     */
    @Test
    void shouldHitWhenContactProximityExists() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-001");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        data.setFields(List.of(
                contactField("F1", "DOC-A", "周工", "13800004821"),
                contactField("F2", "DOC-B", "周工", "13800004871")
        ));

        RuleResult result = executor.execute(data);

        assertEquals(1, result.getHits().size(), "应当命中 1 条风险记录");
        RuleHit hit = result.getHits().get(0);
        assertEquals("W-M2", hit.getRuleCode());
        assertTrue(hit.getTriggerSummary().contains("周工"));
        assertTrue(hit.getTriggerSummary().contains("号段近邻"));
        assertEquals(2, hit.getDocumentIds().size());
        assertEquals(2, hit.getEvidences().size());
    }

    /**
     * 验证完全相同的联系方式也应命中（更严重的迹象）。
     */
    @Test
    void shouldHitWhenContactIdentical() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-002");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        data.setFields(List.of(
                contactField("F1", "DOC-A", "周工", "13800004821"),
                contactField("F2", "DOC-B", "周工", "13800004821")
        ));

        RuleResult result = executor.execute(data);

        assertEquals(1, result.getHits().size());
    }

    /**
     * 验证非同名联系人即使号码接近也不应误报。
     */
    @Test
    void shouldNotHitWhenNamesDifferent() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-003");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        data.setFields(List.of(
                contactField("F1", "DOC-A", "周工", "13800004821"),
                contactField("F2", "DOC-B", "李工", "13800004822")
        ));

        RuleResult result = executor.execute(data);

        assertTrue(result.getHits().isEmpty());
    }

    /**
     * 验证同名但由于号码相差较大（非同号段）时不应误报。
     */
    @Test
    void shouldNotHitWhenPhonesNotProximity() {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-004");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        data.setFields(List.of(
                contactField("F1", "DOC-A", "周工", "13800004821"),
                contactField("F2", "DOC-B", "周工", "13900001234")
        ));

        RuleResult result = executor.execute(data);

        assertTrue(result.getHits().isEmpty());
    }

    private Field contactField(String fieldId, String documentId, String name, String phone) {
        Field field = new Field();
        field.setFieldId(fieldId);
        field.setDocumentId(documentId);
        field.setFieldType("contact_info");
        field.setNormalizedKey(name);
        field.setNormalizedValue(phone);
        field.setChapterPath("第一章/联系方式");
        return field;
    }
}
