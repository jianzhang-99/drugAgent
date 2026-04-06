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
 * TemplateHomologyExecutor 版式模板同源规则（W-M4）单元测试。
 *
 * <p>测试场景：
 * <ol>
 *   <li>目录标题高度重合时应命中</li>
 *   <li>匹配标题数不足5个不应命中</li>
 *   <li>相似度低于80%不应命中</li>
 *   <li>空数据或null应返回空列表</li>
 *   <li>单个文档不比对</li>
 *   <li>非heading类型字段应被过滤</li>
 * </ol>
 */
class TemplateHomologyExecutorTest {

    private TemplateHomologyExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new TemplateHomologyExecutor();
    }

    /**
     * 测试数据准备：构建包含标题字段的TenderReviewData
     */
    private TenderReviewData createTenderReviewDataWithHeadings(
            List<Field> headingFields,
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
        data.setFields(headingFields);

        return data;
    }

    /**
     * 测试：两份文档目录结构完全相同，应命中
     */
    @Test
    void should命中当两份文档目录结构完全相同时() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createHeadingField("h1", "doc-1", "H1:1.", "1. 总体理解"),
                createHeadingField("h2", "doc-1", "H1:2.", "2. 建设目标"),
                createHeadingField("h3", "doc-1", "H1:3.", "3. 技术方案"),
                createHeadingField("h4", "doc-1", "H1:4.", "4. 实施计划"),
                createHeadingField("h5", "doc-1", "H1:5.", "5. 服务保障"),
                createHeadingField("h6", "doc-2", "H1:1.", "1. 总体理解"),
                createHeadingField("h7", "doc-2", "H1:2.", "2. 建设目标"),
                createHeadingField("h8", "doc-2", "H1:3.", "3. 技术方案"),
                createHeadingField("h9", "doc-2", "H1:4.", "4. 实施计划"),
                createHeadingField("h10", "doc-2", "H1:5.", "5. 服务保障")
        );

        TenderReviewData data = createTenderReviewDataWithHeadings(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertEquals(1, hits.size());
        RuleHit hit = hits.get(0);
        assertEquals("W-M4", hit.getRuleCode());
        assertEquals("版式模板同源", hit.getRuleName());
        assertEquals("collusion", hit.getRiskType());
        assertEquals("MEDIUM", hit.getPriority());
        assertEquals(70, hit.getWeight());
    }

    /**
     * 测试：匹配标题数不足5个不应命中
     */
    @Test
    void should不命中当匹配标题数不足5个时() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createHeadingField("h1", "doc-1", "H1:1.", "1. 总体理解"),
                createHeadingField("h2", "doc-1", "H1:2.", "2. 建设目标"),
                createHeadingField("h3", "doc-1", "H1:3.", "3. 技术方案"),
                createHeadingField("h4", "doc-2", "H1:1.", "1. 总体理解"),
                createHeadingField("h5", "doc-2", "H1:2.", "2. 建设目标"),
                createHeadingField("h6", "doc-2", "H1:3.", "3. 技术方案")
        );

        TenderReviewData data = createTenderReviewDataWithHeadings(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        // 只有3个匹配，不足5个
        assertEquals(0, hits.size());
    }

    /**
     * 测试：两份文档目录完全不同，不应命中
     */
    @Test
    void should不命中当两份文档目录完全不同时() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createHeadingField("h1", "doc-1", "H1:1.", "1. 项目概述"),
                createHeadingField("h2", "doc-1", "H1:2.", "2. 需求分析"),
                createHeadingField("h3", "doc-1", "H1:3.", "3. 系统设计"),
                createHeadingField("h4", "doc-1", "H1:4.", "4. 实施方案"),
                createHeadingField("h5", "doc-1", "H1:5.", "5. 验收标准"),
                createHeadingField("h6", "doc-2", "H1:1.", "A. 公司简介"),
                createHeadingField("h7", "doc-2", "H1:2.", "B. 资质证书"),
                createHeadingField("h8", "doc-2", "H1:3.", "C. 项目案例"),
                createHeadingField("h9", "doc-2", "H1:4.", "D. 报价清单"),
                createHeadingField("h10", "doc-2", "H1:5.", "E. 售后服务")
        );

        TenderReviewData data = createTenderReviewDataWithHeadings(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertEquals(0, hits.size());
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
                createHeadingField("h1", "doc-1", "H1:1.", "1. 总体理解"),
                createHeadingField("h2", "doc-1", "H1:2.", "2. 建设目标"),
                createHeadingField("h3", "doc-1", "H1:3.", "3. 技术方案"),
                createHeadingField("h4", "doc-1", "H1:4.", "4. 实施计划"),
                createHeadingField("h5", "doc-1", "H1:5.", "5. 服务保障")
        );

        TenderReviewData data = createTenderReviewDataWithHeadings(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        assertTrue(hits.isEmpty());
    }

    /**
     * 测试：非heading类型字段应被过滤
     */
    @Test
    void should过滤非Heading类型字段() {
        List<String> docIds = List.of("doc-1", "doc-2");
        List<Field> fields = List.of(
                createHeadingField("h1", "doc-1", "H1:1.", "1. 总体理解"),
                createHeadingField("h2", "doc-1", "H1:2.", "2. 建设目标"),
                createHeadingField("h3", "doc-1", "H1:3.", "3. 技术方案"),
                createHeadingField("h4", "doc-1", "H1:4.", "4. 实施计划"),
                createHeadingField("h5", "doc-1", "H1:5.", "5. 服务保障"),
                createHeadingField("h6", "doc-2", "H1:1.", "1. 总体理解"),
                createHeadingField("h7", "doc-2", "H1:2.", "2. 建设目标"),
                createHeadingField("h8", "doc-2", "H1:3.", "3. 技术方案"),
                createHeadingField("h9", "doc-2", "H1:4.", "4. 实施计划"),
                createHeadingField("h10", "doc-2", "H1:5.", "5. 服务保障"),
                // 非heading类型
                Field.builder()
                        .fieldId("field-1")
                        .documentId("doc-1")
                        .fieldType("contact_info")
                        .normalizedKey("张三")
                        .normalizedValue("13812345678")
                        .build()
        );

        TenderReviewData data = createTenderReviewDataWithHeadings(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        // 非heading字段被过滤，但5个heading仍能匹配
        assertEquals(1, hits.size());
    }

    /**
     * 测试：多份文档（3份）两两比对
     */
    @Test
    void should对多份文档进行两两比对() {
        List<String> docIds = List.of("doc-1", "doc-2", "doc-3");
        List<Field> fields = List.of(
                createHeadingField("h1", "doc-1", "H1:1.", "1. 总体理解"),
                createHeadingField("h2", "doc-1", "H1:2.", "2. 建设目标"),
                createHeadingField("h3", "doc-1", "H1:3.", "3. 技术方案"),
                createHeadingField("h4", "doc-1", "H1:4.", "4. 实施计划"),
                createHeadingField("h5", "doc-1", "H1:5.", "5. 服务保障"),
                createHeadingField("h6", "doc-2", "H1:1.", "1. 总体理解"),
                createHeadingField("h7", "doc-2", "H1:2.", "2. 建设目标"),
                createHeadingField("h8", "doc-2", "H1:3.", "3. 技术方案"),
                createHeadingField("h9", "doc-2", "H1:4.", "4. 实施计划"),
                createHeadingField("h10", "doc-2", "H1:5.", "5. 服务保障"),
                // doc-3与其他都不同
                createHeadingField("h11", "doc-3", "H1:1.", "A. 项目概述"),
                createHeadingField("h12", "doc-3", "H1:2.", "B. 需求分析"),
                createHeadingField("h13", "doc-3", "H1:3.", "C. 系统设计"),
                createHeadingField("h14", "doc-3", "H1:4.", "D. 实施方案"),
                createHeadingField("h15", "doc-3", "H1:5.", "E. 验收标准")
        );

        TenderReviewData data = createTenderReviewDataWithHeadings(fields, docIds);
        List<RuleHit> hits = executor.execute(data);

        // 只有doc-1与doc-2匹配
        assertEquals(1, hits.size());
    }

    /**
     * 创建标题字段的辅助方法
     */
    private Field createHeadingField(String fieldId, String docId, String level, String title) {
        return Field.builder()
                .fieldId(fieldId)
                .documentId(docId)
                .blockId("block-" + fieldId)
                .fieldType("heading")
                .normalizedKey(level)
                .normalizedValue(title)
                .chapterPath("目录")
                .anchorParagraphIndex(1)
                .anchorTableIndex(-1)
                .anchorPageNo(1)
                .anchorSectionNo("目录")
                .anchorParagraphNo(1)
                .anchorTableNo(-1)
                .build();
    }
}
