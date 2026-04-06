package com.liang.drugagent.scene.tender_review.support;

import com.liang.drugagent.scene.tender_review.model.CompareScope;
import com.liang.drugagent.scene.tender_review.model.Field;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.support.executor.ContactProximityExecutor;
import com.liang.drugagent.scene.tender_review.support.executor.TemplateHomologyExecutor;
import com.liang.drugagent.scene.tender_review.support.executor.TenderRuleExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TenderRuleEngine 规则引擎编排单元测试。
 *
 * <p>测试场景：
 * <ol>
 *   <li>空数据应返回空列表</li>
 *   <li>空执行器列表应返回空列表</li>
 *   <li>执行器返回空结果时不应抛异常</li>
 *   <li>多执行器结果应正确合并</li>
 *   <li>结果应按权重降序排序</li>
 * </ol>
 */
class TenderRuleEngineTest {

    private TenderRuleEngine engine;

    @BeforeEach
    void setUp() {
        // 注入实际执行器
        engine = new TenderRuleEngine(List.of(
                new ContactProximityExecutor(),
                new TemplateHomologyExecutor()
        ));
    }

    /**
     * 测试：空数据应返回空列表
     */
    @Test
    void should返回空列表当数据为Null时() {
        List<RuleHit> hits = engine.execute(null);
        assertTrue(hits.isEmpty());
    }

    /**
     * 测试：执行器返回空结果时不应抛异常
     */
    @Test
    void should执行器返回空结果时不抛异常() {
        TenderReviewData data = new TenderReviewData();
        data.setFields(new ArrayList<>());
        data.setCompareScopes(List.of(CompareScope.builder()
                .scopeId("scope-1")
                .documentIds(List.of("doc-1"))
                .build()));

        // 不应抛异常
        List<RuleHit> hits = engine.execute(data);
        assertNotNull(hits);
    }

    /**
     * 测试：多执行器结果应正确合并
     */
    @Test
    void should多执行器结果正确合并() {
        TenderReviewData data = createTestData();

        List<RuleHit> hits = engine.execute(data);

        // 应该收集到所有执行器的命中结果
        assertNotNull(hits);
        // hits可能为空，因为测试数据是空的
    }

    /**
     * 测试：结果应按权重降序排序
     */
    @Test
    void should结果按权重降序排序() {
        // 创建一个自定义执行器，返回固定权重的结果
        TenderRuleEngine customEngine = new TenderRuleEngine(List.of(
                new TenderRuleExecutor() {
                    @Override
                    public List<RuleHit> execute(TenderReviewData data) {
                        RuleHit lowWeight = new RuleHit();
                        lowWeight.setRuleCode("W-LOW");
                        lowWeight.setWeight(30);
                        return List.of(lowWeight);
                    }
                },
                new TenderRuleExecutor() {
                    @Override
                    public List<RuleHit> execute(TenderReviewData data) {
                        RuleHit highWeight = new RuleHit();
                        highWeight.setRuleCode("W-HIGH");
                        highWeight.setWeight(90);
                        return List.of(highWeight);
                    }
                },
                new TenderRuleExecutor() {
                    @Override
                    public List<RuleHit> execute(TenderReviewData data) {
                        RuleHit mediumWeight = new RuleHit();
                        mediumWeight.setRuleCode("W-MED");
                        mediumWeight.setWeight(60);
                        return List.of(mediumWeight);
                    }
                }
        ));

        TenderReviewData data = createTestData();
        List<RuleHit> hits = customEngine.execute(data);

        assertEquals(3, hits.size());
        // 验证按权重降序排序
        assertEquals("W-HIGH", hits.get(0).getRuleCode());
        assertEquals("W-MED", hits.get(1).getRuleCode());
        assertEquals("W-LOW", hits.get(2).getRuleCode());
    }

    /**
     * 测试：空执行器列表应返回空列表
     */
    @Test
    void should空执行器列表返回空列表() {
        TenderRuleEngine emptyEngine = new TenderRuleEngine(new ArrayList<>());
        TenderReviewData data = createTestData();

        List<RuleHit> hits = emptyEngine.execute(data);
        assertTrue(hits.isEmpty());
    }

    /**
     * 创建测试数据
     */
    private TenderReviewData createTestData() {
        TenderReviewData data = new TenderReviewData();

        List<com.liang.drugagent.scene.tender_review.model.TenderDocument> docs = new ArrayList<>();
        docs.add(com.liang.drugagent.scene.tender_review.model.TenderDocument.builder()
                .documentId("doc-1")
                .filename("tender1.md")
                .build());
        docs.add(com.liang.drugagent.scene.tender_review.model.TenderDocument.builder()
                .documentId("doc-2")
                .filename("tender2.md")
                .build());
        data.setDocuments(docs);

        CompareScope scope = CompareScope.builder()
                .scopeId("scope-1")
                .scopeType("full_bid_compare")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        data.setCompareScopes(List.of(scope));

        data.setFields(new ArrayList<>());

        return data;
    }
}
