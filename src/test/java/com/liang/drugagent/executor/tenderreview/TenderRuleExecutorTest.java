package com.liang.drugagent.executor.tenderreview;

import com.liang.drugagent.domain.tenderreview.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 标书审查规则执行器单元测试。
 * 测试各个规则执行器是否能正常命中异常。
 *
 * @author liangjiajian
 */
class TenderRuleExecutorTest {

    // ========== W-M2 联系方式近邻测试 ==========

    /**
     * 测试 W-M2 联系方式近邻规则 - 相同联系人+相同电话应命中
     */
    @Test
    void testContactProximity_sameContactAndPhone_shouldHit() {
        ContactProximityExecutor executor = new ContactProximityExecutor();

        TenderReviewData data = createTestDataForContactProximity(
                "周经理", "13800004821",
                "周经理", "13800004821",
                "DOC-A", "DOC-B"
        );

        RuleResult result = executor.execute(data);

        assertFalse(result.getHits().isEmpty(), "相同联系人+相同电话应命中");
        RuleHit hit = result.getHits().get(0);
        assertEquals("W-M2", hit.getRuleCode());
        assertEquals("联系方式近邻", hit.getRuleName());
        assertTrue(hit.getTriggerSummary().contains("周经理"));
    }

    /**
     * 测试 W-M2 联系方式近邻规则 - 相同联系人+号段近邻应命中
     */
    @Test
    void testContactProximity_sameContactAndProximityPhone_shouldHit() {
        ContactProximityExecutor executor = new ContactProximityExecutor();

        TenderReviewData data = createTestDataForContactProximity(
                "周经理", "13800004821",
                "周经理", "13800004822",
                "DOC-A", "DOC-B"
        );

        RuleResult result = executor.execute(data);

        assertFalse(result.getHits().isEmpty(), "相同联系人+号段近邻应命中");
    }

    /**
     * 测试 W-M2 联系方式近邻规则 - 不同联系人应不命中
     */
    @Test
    void testContactProximity_differentContact_shouldNotHit() {
        ContactProximityExecutor executor = new ContactProximityExecutor();

        TenderReviewData data = createTestDataForContactProximity(
                "周经理", "13800004821",
                "王经理", "13800004821",
                "DOC-A", "DOC-B"
        );

        RuleResult result = executor.execute(data);

        assertTrue(result.getHits().isEmpty(), "不同联系人应不命中");
    }

    // ========== W-M3 核心团队重叠测试 ==========

    /**
     * 测试 W-M3 核心团队重叠规则 - 相同成员姓名应命中
     */
    @Test
    void testCoreTeamOverlap_sameMemberName_shouldHit() {
        CoreTeamOverlapExecutor executor = new CoreTeamOverlapExecutor();

        TenderReviewData data = createTestDataForCoreTeamOverlap(
                "章辰浩", "项目经理，12年经验",
                "章辰浩", "项目经理，12年经验",
                "DOC-A", "DOC-B"
        );

        RuleResult result = executor.execute(data);

        assertFalse(result.getHits().isEmpty(), "相同成员姓名应命中");
        RuleHit hit = result.getHits().get(0);
        assertEquals("W-M3", hit.getRuleCode());
        assertEquals("核心团队重叠", hit.getRuleName());
    }

    /**
     * 测试 W-M3 核心团队重叠规则 - 不同成员姓名应不命中
     */
    @Test
    void testCoreTeamOverlap_differentMemberName_shouldNotHit() {
        CoreTeamOverlapExecutor executor = new CoreTeamOverlapExecutor();

        TenderReviewData data = createTestDataForCoreTeamOverlap(
                "章辰浩", "项目经理，12年经验",
                "王建国", "项目经理，10年经验",
                "DOC-A", "DOC-B"
        );

        RuleResult result = executor.execute(data);

        assertTrue(result.getHits().isEmpty(), "不同成员姓名应不命中");
    }

    // ========== W-M1 报价梯度异常测试 ==========

    /**
     * 测试 W-M1 报价梯度异常规则 - 存在固定价差应命中
     */
    @Test
    void testQuoteGradient_fixedDelta_shouldHit() {
        QuoteGradientExecutor executor = new QuoteGradientExecutor();

        TenderReviewData data = createTestDataForQuoteGradient(
                "需求调研", new BigDecimal("195000"),
                "需求调研", new BigDecimal("200000"),
                "DOC-A", "DOC-B"
        );
        // 添加更多报价项以满足最小比较数量
        addQuoteField(data, "平台开发", new BigDecimal("845000"), "平台开发", new BigDecimal("850000"));
        addQuoteField(data, "接口集成", new BigDecimal("275000"), "接口集成", new BigDecimal("280000"));

        RuleResult result = executor.execute(data);

        assertFalse(result.getHits().isEmpty(), "存在固定价差应命中");
        RuleHit hit = result.getHits().get(0);
        assertEquals("W-M1", hit.getRuleCode());
        assertEquals("报价梯度异常", hit.getRuleName());
    }

    /**
     * 测试 W-M1 报价梯度异常规则 - 无固定价差应不命中
     */
    @Test
    void testQuoteGradient_noFixedDelta_shouldNotHit() {
        QuoteGradientExecutor executor = new QuoteGradientExecutor();

        TenderReviewData data = createTestDataForQuoteGradient(
                "需求调研", new BigDecimal("195000"),
                "需求调研", new BigDecimal("250000"),
                "DOC-A", "DOC-B"
        );
        addQuoteField(data, "平台开发", new BigDecimal("845000"), "平台开发", new BigDecimal("917000"));
        addQuoteField(data, "接口集成", new BigDecimal("275000"), "接口集成", new BigDecimal("335000"));

        RuleResult result = executor.execute(data);

        assertTrue(result.getHits().isEmpty(), "无固定价差应不命中");
    }

    // ========== W-M4 版式模板同源测试 ==========

    /**
     * 测试 W-M4 版式模板同源规则 - 目录高度相似应命中
     */
    @Test
    void testTemplateHomology_highSimilarity_shouldHit() {
        TemplateHomologyExecutor executor = new TemplateHomologyExecutor();

        TenderReviewData data = createTestDataForTemplateHomology(
                List.of("一、项目理解", "二、建设目标", "三、技术方案", "四、实施计划", "五、团队配置", "六、报价", "七、服务承诺"),
                List.of("一、项目理解", "二、建设目标", "三、技术方案", "四、实施计划", "五、团队配置", "六、报价", "七、服务承诺"),
                "DOC-A", "DOC-B"
        );

        RuleResult result = executor.execute(data);

        assertFalse(result.getHits().isEmpty(), "目录高度相似应命中");
        RuleHit hit = result.getHits().get(0);
        assertEquals("W-M4", hit.getRuleCode());
        assertEquals("版式模板同源", hit.getRuleName());
    }

    /**
     * 测试 W-M4 版式模板同源规则 - 目录差异大应不命中
     */
    @Test
    void testTemplateHomology_lowSimilarity_shouldNotHit() {
        TemplateHomologyExecutor executor = new TemplateHomologyExecutor();

        TenderReviewData data = createTestDataForTemplateHomology(
                List.of("一、项目理解", "二、建设目标", "三、技术方案"),
                List.of("一、投标函", "二、资质证明", "三、报价单"),
                "DOC-A", "DOC-B"
        );

        RuleResult result = executor.execute(data);

        assertTrue(result.getHits().isEmpty(), "目录差异大应不命中");
    }

    // ========== W-M5 罕见错误共现测试 ==========

    /**
     * 测试 W-M5 罕见错误共现规则 - 相同错别字应命中
     */
    @Test
    void testRareTypoCooccurrence_sameTypo_shouldHit() {
        RareTypoCooccurrenceExecutor executor = new RareTypoCooccurrenceExecutor();

        TenderReviewData data = createTestDataForRareTypoCooccurrence(
                "应急响映",
                "应急响映",
                "DOC-A", "DOC-B"
        );

        RuleResult result = executor.execute(data);

        assertFalse(result.getHits().isEmpty(), "相同错别字应命中");
        RuleHit hit = result.getHits().get(0);
        assertEquals("W-M5", hit.getRuleCode());
        assertEquals("罕见错误共现", hit.getRuleName());
    }

    /**
     * 测试 W-M5 罕见错误共现规则 - 不同错别字应不命中
     */
    @Test
    void testRareTypoCooccurrence_differentTypo_shouldNotHit() {
        RareTypoCooccurrenceExecutor executor = new RareTypoCooccurrenceExecutor();

        TenderReviewData data = createTestDataForRareTypoCooccurrence(
                "应急响映",
                "贯串执行",
                "DOC-A", "DOC-B"
        );

        RuleResult result = executor.execute(data);

        assertTrue(result.getHits().isEmpty(), "不同错别字应不命中");
    }

    // ========== 辅助方法 ==========

    private TenderReviewData createTestDataForContactProximity(
            String name1, String phone1, String name2, String phone2, String docId1, String docId2) {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("SCOPE-001");
        scope.setDocumentIds(List.of(docId1, docId2));
        data.setCompareScopes(List.of(scope));

        Field field1 = Field.builder()
                .fieldId("F-001")
                .documentId(docId1)
                .fieldType("contact_info")
                .normalizedKey(name1.trim())
                .normalizedValue(phone1)
                .build();

        Field field2 = Field.builder()
                .fieldId("F-002")
                .documentId(docId2)
                .fieldType("contact_info")
                .normalizedKey(name2.trim())
                .normalizedValue(phone2)
                .build();

        data.setFields(List.of(field1, field2));
        return data;
    }

    private TenderReviewData createTestDataForCoreTeamOverlap(
            String name1, String resume1, String name2, String resume2, String docId1, String docId2) {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("SCOPE-001");
        scope.setDocumentIds(List.of(docId1, docId2));
        data.setCompareScopes(List.of(scope));

        Field field1 = Field.builder()
                .fieldId("F-001")
                .documentId(docId1)
                .fieldType("team_member")
                .normalizedKey(name1)
                .normalizedValue(resume1)
                .build();

        Field field2 = Field.builder()
                .fieldId("F-002")
                .documentId(docId2)
                .fieldType("team_member")
                .normalizedKey(name2)
                .normalizedValue(resume2)
                .build();

        data.setFields(List.of(field1, field2));
        return data;
    }

    private TenderReviewData createTestDataForQuoteGradient(
            String item1, BigDecimal value1, String item2, BigDecimal value2, String docId1, String docId2) {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("SCOPE-001");
        scope.setDocumentIds(new ArrayList<>(List.of(docId1, docId2)));
        data.setCompareScopes(new ArrayList<>(List.of(scope)));

        Field field1 = Field.builder()
                .fieldId("F-001")
                .documentId(docId1)
                .blockId("BLOCK-001")
                .fieldType("quote_item")
                .normalizedKey(item1)
                .normalizedValue(value1.toPlainString())
                .build();

        Field field2 = Field.builder()
                .fieldId("F-002")
                .documentId(docId2)
                .blockId("BLOCK-002")
                .fieldType("quote_item")
                .normalizedKey(item2)
                .normalizedValue(value2.toPlainString())
                .build();

        data.setFields(new ArrayList<>(List.of(field1, field2)));
        return data;
    }

    private void addQuoteField(TenderReviewData data, String item1, BigDecimal value1, String item2, BigDecimal value2) {
        String docId1 = data.getCompareScopes().get(0).getDocumentIds().get(0);
        String docId2 = data.getCompareScopes().get(0).getDocumentIds().get(1);

        Field field1 = Field.builder()
                .fieldId("F-" + (data.getFields().size() + 1))
                .documentId(docId1)
                .blockId("BLOCK-" + (data.getFields().size() + 1))
                .fieldType("quote_item")
                .normalizedKey(item1)
                .normalizedValue(value1.toPlainString())
                .build();

        Field field2 = Field.builder()
                .fieldId("F-" + (data.getFields().size() + 2))
                .documentId(docId2)
                .blockId("BLOCK-" + (data.getFields().size() + 2))
                .fieldType("quote_item")
                .normalizedKey(item2)
                .normalizedValue(value2.toPlainString())
                .build();

        data.getFields().add(field1);
        data.getFields().add(field2);
    }

    private TenderReviewData createTestDataForTemplateHomology(
            List<String> headings1, List<String> headings2, String docId1, String docId2) {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("SCOPE-001");
        scope.setDocumentIds(List.of(docId1, docId2));
        data.setCompareScopes(List.of(scope));

        java.util.ArrayList<Field> fields = new java.util.ArrayList<>();
        int idx = 1;
        for (String h : headings1) {
            fields.add(Field.builder()
                    .fieldId("F-" + (idx++))
                    .documentId(docId1)
                    .fieldType("heading")
                    .normalizedValue(h)
                    .build());
        }
        for (String h : headings2) {
            fields.add(Field.builder()
                    .fieldId("F-" + (idx++))
                    .documentId(docId2)
                    .fieldType("heading")
                    .normalizedValue(h)
                    .build());
        }

        data.setFields(fields);
        return data;
    }

    private TenderReviewData createTestDataForRareTypoCooccurrence(
            String typo1, String typo2, String docId1, String docId2) {
        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("SCOPE-001");
        scope.setDocumentIds(List.of(docId1, docId2));
        data.setCompareScopes(List.of(scope));

        Field field1 = Field.builder()
                .fieldId("F-001")
                .documentId(docId1)
                .fieldType("typo")
                .normalizedValue(typo1)
                .build();

        Field field2 = Field.builder()
                .fieldId("F-002")
                .documentId(docId2)
                .fieldType("typo")
                .normalizedValue(typo2)
                .build();

        data.setFields(List.of(field1, field2));
        return data;
    }
}
