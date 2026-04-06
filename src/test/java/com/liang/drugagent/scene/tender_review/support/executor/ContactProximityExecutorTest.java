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
 * ContactProximityExecutor 联系方式近邻规则（W-M2）单元测试。
 *
 * <p>测试场景：
 * <ol>
 *   <li>正常命中：联系人姓名相同且电话号码同号段</li>
 *   <li>完全相同号码也应命中</li>
 *   <li>姓名不同不应命中</li>
 *   <li>号码号段不同不应命中</li>
 *   <li>空数据或null应返回空列表</li>
 *   <li>单个文档不比对</li>
 *   <li>字段类型过滤：只处理contact_info类型</li>
 * </ol>
 */
class ContactProximityExecutorTest {

    private ContactProximityExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ContactProximityExecutor();
    }

    /**
     * 测试数据准备：构建包含联系方式的TenderReviewData
     */
    private TenderReviewData createTenderReviewDataWithContacts(
            List<Field> contactFields,
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
        data.setFields(contactFields);

        return data;
    }

    /**
     * 测试：联系人姓名相同且电话号码完全一致，应命中
     */
    @Test
    void should命中当联系人姓名相同且号码一致时() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createContactField("field-1", "doc-1", "张三", "13812345678"),
                createContactField("field-2", "doc-2", "张三", "13812345678")
        );

        TenderReviewData data = createTenderReviewDataWithContacts(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertEquals(1, hits.size());
        RuleHit hit = hits.get(0);
        assertEquals("W-M2", hit.getRuleCode());
        assertEquals("联系方式近邻", hit.getRuleName());
        assertEquals("collusion", hit.getRiskType());
        assertEquals("VERY_HIGH", hit.getPriority());
        assertEquals(90, hit.getWeight());
        assertTrue(hit.getTriggerSummary().contains("张三"));
    }

    /**
     * 测试：联系人姓名相同但号码不同号段，不应命中
     */
    @Test
    void should不命中当联系人姓名相同但号码不同号段时() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createContactField("field-1", "doc-1", "张三", "13812345678"),
                createContactField("field-2", "doc-2", "张三", "13912345678") // 不同号段
        );

        TenderReviewData data = createTenderReviewDataWithContacts(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertEquals(0, hits.size());
    }

    /**
     * 测试：联系人姓名不同，即使号码相近也不应命中
     */
    @Test
    void should不命中当联系人姓名不同时() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createContactField("field-1", "doc-1", "张三", "13812345678"),
                createContactField("field-2", "doc-2", "李四", "13812345678") // 姓名不同
        );

        TenderReviewData data = createTenderReviewDataWithContacts(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertEquals(0, hits.size());
    }

    /**
     * 测试：电话号码号段相同但末两位不同，应命中
     */
    @Test
    void should命中当电话号码号段相同末两位不同时() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createContactField("field-1", "doc-1", "张三", "13812345678"),
                createContactField("field-2", "doc-2", "张三", "13812345679") // 末位不同
        );

        TenderReviewData data = createTenderReviewDataWithContacts(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertEquals(1, hits.size());
    }

    /**
     * 测试：电话号码号段相同但末三位不同，不应命中（号段要求前9位）
     */
    @Test
    void should不命中当电话号码号段不同末三位也不同时() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createContactField("field-1", "doc-1", "张三", "13812345678"),
                createContactField("field-2", "doc-2", "张三", "13812345779") // 末两位也不同
        );

        TenderReviewData data = createTenderReviewDataWithContacts(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertEquals(0, hits.size());
    }

    /**
     * 测试：多份文档（3份）两两比对
     */
    @Test
    void should对多份文档进行两两比对() {
        List<String> docIds = List.of("doc-1", "doc-2", "doc-3");
        List<Field> fields = List.of(
                createContactField("field-1", "doc-1", "张三", "13812345678"),
                createContactField("field-2", "doc-2", "张三", "13812345678"), // 与doc-1匹配
                createContactField("field-3", "doc-3", "李四", "13912345678")  // 与前两个都不匹配
        );

        TenderReviewData data = createTenderReviewDataWithContacts(fields, docIds);
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
                createContactField("field-1", "doc-1", "张三", "13812345678")
        );

        TenderReviewData data = createTenderReviewDataWithContacts(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertTrue(hits.isEmpty());
    }

    /**
     * 测试：非contact_info类型字段应被过滤
     */
    @Test
    void should过滤非ContactInfo类型字段() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createContactField("field-1", "doc-1", "张三", "13812345678"),
                createContactField("field-2", "doc-2", "张三", "13812345678"),
                // 非contact_info类型的字段
                Field.builder()
                        .fieldId("field-3")
                        .documentId("doc-1")
                        .fieldType("team_member")
                        .normalizedKey("王五")
                        .normalizedValue("13912345678")
                        .build()
        );

        TenderReviewData data = createTenderReviewDataWithContacts(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        // 仍然应该命中，因为前两个字段是contact_info
        assertEquals(1, hits.size());
    }

    /**
     * 测试：电话号码带空格和横杠也能正确匹配
     */
    @Test
    void should电话号码标准化后正确匹配() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createContactField("field-1", "doc-1", "张三", "138-1234-5678"),
                createContactField("field-2", "doc-2", "张三", "138 1234 5679")
        );

        TenderReviewData data = createTenderReviewDataWithContacts(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        // 标准化后号码前9位相同，末位不同
        assertEquals(1, hits.size());
    }

    /**
     * 创建联系方式字段的辅助方法
     */
    private Field createContactField(String fieldId, String docId, String name, String phone) {
        return Field.builder()
                .fieldId(fieldId)
                .documentId(docId)
                .blockId("block-" + fieldId)
                .fieldType("contact_info")
                .normalizedKey(name)
                .normalizedValue(phone)
                .chapterPath("1.投标文件")
                .anchorParagraphIndex(1)
                .anchorTableIndex(-1)
                .anchorPageNo(1)
                .anchorSectionNo("1")
                .anchorParagraphNo(1)
                .anchorTableNo(-1)
                .build();
    }
}
