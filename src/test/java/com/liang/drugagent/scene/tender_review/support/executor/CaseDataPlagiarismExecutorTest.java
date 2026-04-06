package com.liang.drugagent.scene.tender_review.support.executor;

import com.liang.drugagent.scene.tender_review.model.CompareScope;
import com.liang.drugagent.scene.tender_review.model.Field;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CaseDataPlagiarismExecutor 案例数据抄袭规则（W-P6）单元测试。
 *
 * <p>测试场景：
 * <ol>
 *   <li>相同案例数据（如"8家供应商"）应命中</li>
 *   <li>不含数字和量词的案例数据不应命中</li>
 *   <li>不同案例数据不应命中</li>
 *   <li>空数据或null应返回空列表</li>
 *   <li>单个文档不比对</li>
 *   <li>非case_data类型字段应被过滤</li>
 * </ol>
 */
class CaseDataPlagiarismExecutorTest {

    private CaseDataPlagiarismExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new CaseDataPlagiarismExecutor();
    }

    /**
     * 测试数据准备：构建包含案例数据字段的TenderReviewData
     */
    private TenderReviewData createTenderReviewDataWithCaseData(
            List<Field> caseDataFields,
            List<String> docIds) {
        TenderReviewData data = new TenderReviewData();

        // 创建文档
        List<com.liang.drugagent.scene.tender_review.model.TenderDocument> docs = new ArrayList<>();
        for (String docId : docIds) {
            docs.add(com.liang.drugagent.scene.tender_review.model.TenderDocument.builder()
                    .documentId(docId)
                    .filename(docId + ".md")
                    .build());
        }
        data.setDocuments(docs);

        // 创建比对范围
        CompareScope scope = CompareScope.builder()
                .scopeId("scope-1")
                .scopeType("full_bid_compare")
                .documentIds(docIds)
                .build();
        data.setCompareScopes(List.of(scope));

        // 设置字段
        data.setFields(caseDataFields);

        return data;
    }

    /**
     * 测试：两份文档案例数据完全一致（包含特定量词指标），应命中
     */
    @Test
    void should命中当案例数据一致且包含量词指标时() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createCaseDataField("field-1", "doc-1", "案例1", "8家供应商"),
                createCaseDataField("field-2", "doc-2", "案例1", "8家供应商")
        );

        TenderReviewData data = createTenderReviewDataWithCaseData(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertEquals(1, hits.size());
        RuleHit hit = hits.get(0);
        assertEquals("W-P6", hit.getRuleCode());
        assertEquals("案例数据抄袭", hit.getRuleName());
        assertEquals("plagiarism", hit.getRiskType());
        assertEquals("MEDIUM_HIGH", hit.getPriority());
        assertEquals(90, hit.getWeight());
        assertTrue(hit.getTriggerSummary().contains("8家供应商"));
    }

    /**
     * 测试：不同案例数据不应命中
     */
    @Test
    void should不命中当案例数据不同时() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createCaseDataField("field-1", "doc-1", "案例1", "8家供应商"),
                createCaseDataField("field-2", "doc-2", "案例1", "10家供应商")
        );

        TenderReviewData data = createTenderReviewDataWithCaseData(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertEquals(0, hits.size());
    }

    /**
     * 测试：不包含数字和量词的案例数据不应命中
     */
    @Test
    void should不命中当案例数据不包含数字和量词时() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createCaseDataField("field-1", "doc-1", "案例1", "丰富的行业经验"),
                createCaseDataField("field-2", "doc-2", "案例1", "丰富的行业经验")
        );

        TenderReviewData data = createTenderReviewDataWithCaseData(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        // 不符合"数字+量词"模式，不应命中
        assertEquals(0, hits.size());
    }

    /**
     * 测试：多种量词指标都应支持匹配
     */
    @Test
    void should支持各种量词指标匹配() {
        List<String> docIds = List.of("doc-1", "doc-2");

        // 测试"省"作为量词
        List<Field> fields1 = List.of(
                createCaseDataField("field-1", "doc-1", "案例1", "15个省"),
                createCaseDataField("field-2", "doc-2", "案例1", "15个省")
        );
        TenderReviewData data1 = createTenderReviewDataWithCaseData(fields1, docIds);
        assertEquals(1, executor.execute(data1).size());

        // 测试"中心"作为量词
        List<Field> fields2 = List.of(
                createCaseDataField("field-1", "doc-1", "案例1", "3个中心"),
                createCaseDataField("field-2", "doc-2", "案例1", "3个中心")
        );
        TenderReviewData data2 = createTenderReviewDataWithCaseData(fields2, docIds);
        assertEquals(1, executor.execute(data2).size());

        // 测试"座"作为量词
        List<Field> fields3 = List.of(
                createCaseDataField("field-1", "doc-1", "案例1", "5座工厂"),
                createCaseDataField("field-2", "doc-2", "案例1", "5座工厂")
        );
        TenderReviewData data3 = createTenderReviewDataWithCaseData(fields3, docIds);
        assertEquals(1, executor.execute(data3).size());

        // 测试"余"作为量词时不被支持（需使用[家座个省中心]）
        List<Field> fields4 = List.of(
                createCaseDataField("field-1", "doc-1", "案例1", "600家客户"),
                createCaseDataField("field-2", "doc-2", "案例1", "600家客户")
        );
        TenderReviewData data4 = createTenderReviewDataWithCaseData(fields4, docIds);
        assertEquals(1, executor.execute(data4).size());
    }

    /**
     * 测试：多份文档（3份）两两比对
     */
    @Test
    void should对多份文档进行两两比对() {
        List<String> docIds = List.of("doc-1", "doc-2", "doc-3");
        List<Field> fields = List.of(
                createCaseDataField("field-1", "doc-1", "案例1", "8家供应商"),
                createCaseDataField("field-2", "doc-2", "案例1", "8家供应商"), // 与doc-1匹配
                createCaseDataField("field-3", "doc-3", "案例1", "10家供应商")  // 与前两个都不匹配
        );

        TenderReviewData data = createTenderReviewDataWithCaseData(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        // 只有doc-1与doc-2匹配
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).getDocumentIds().contains("doc-1"));
        assertTrue(hits.get(0).getDocumentIds().contains("doc-2"));
    }

    /**
     * 测试：空数据应返回空列表
     */
    @Test
    void should返回空列表当数据为Null时() {
        List<RuleHit> hits = executor.execute(null);
        assertTrue(hits.isEmpty());
    }

    /**
     * 测试：字段列表为空应返回空列表
     */
    @Test
    void should返回空列表当字段列表为空时() {
        TenderReviewData data = new TenderReviewData();
        data.setFields(new ArrayList<>());
        data.setCompareScopes(List.of(CompareScope.builder()
                .scopeId("scope-1")
                .documentIds(List.of("doc-1", "doc-2"))
                .build()));

        List<RuleHit> hits = executor.execute(data);
        assertTrue(hits.isEmpty());
    }

    /**
     * 测试：单个文档不比对
     */
    @Test
    void should返回空列表当只有单个文档时() {
        List<String> docIds = List.of("doc-1");
        List<Field> fields = List.of(
                createCaseDataField("field-1", "doc-1", "案例1", "8家供应商")
        );

        TenderReviewData data = createTenderReviewDataWithCaseData(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertTrue(hits.isEmpty());
    }

    /**
     * 测试：非case_data类型字段应被过滤
     */
    @Test
    void should过滤非CaseData类型字段() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createCaseDataField("field-1", "doc-1", "案例1", "8家供应商"),
                createCaseDataField("field-2", "doc-2", "案例1", "8家供应商"),
                // 非case_data类型
                Field.builder()
                        .fieldId("field-3")
                        .documentId("doc-1")
                        .fieldType("contact_info")
                        .normalizedKey("张三")
                        .normalizedValue("13812345678")
                        .build()
        );

        TenderReviewData data = createTenderReviewDataWithCaseData(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        // 仍然应该命中，因为前两个字段是case_data
        assertEquals(1, hits.size());
    }

    /**
     * 测试：null字段值不应导致异常
     */
    @Test
    void should处理Null字段值不抛异常() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                Field.builder()
                        .fieldId("field-1")
                        .documentId("doc-1")
                        .fieldType("case_data")
                        .normalizedKey("案例1")
                        .normalizedValue(null)
                        .build(),
                Field.builder()
                        .fieldId("field-2")
                        .documentId("doc-2")
                        .fieldType("case_data")
                        .normalizedKey("案例1")
                        .normalizedValue(null)
                        .build()
        );

        TenderReviewData data = createTenderReviewDataWithCaseData(fields, docIds);

        // 不应抛出异常
        List<RuleHit> hits = executor.execute(data);
        assertTrue(hits.isEmpty());
    }

    /**
     * 创建案例数据字段的辅助方法
     */
    private Field createCaseDataField(String fieldId, String docId, String key, String value) {
        return Field.builder()
                .fieldId(fieldId)
                .documentId(docId)
                .blockId("block-" + fieldId)
                .fieldType("case_data")
                .normalizedKey(key)
                .normalizedValue(value)
                .chapterPath("3. 业绩案例")
                .anchorParagraphIndex(1)
                .anchorTableIndex(-1)
                .anchorPageNo(1)
                .anchorSectionNo("3")
                .anchorParagraphNo(1)
                .anchorTableNo(-1)
                .build();
    }
}
