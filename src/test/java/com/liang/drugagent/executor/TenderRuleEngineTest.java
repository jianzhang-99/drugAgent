package com.liang.drugagent.executor;

import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.engine.TenderRuleEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 标书审查规则引擎单元测试。
 *
 * @author liangjiajian
 */
class TenderRuleEngineTest {

    /**
     * 验证规则引擎可以汇总已注册执行器的结果。
     */
    @Test
    void shouldAggregateHitsFromExecutors() {
        TenderRuleEngine ruleEngine = new TenderRuleEngine(List.of(
                data -> {
                    RuleHit hit = new RuleHit();
                    hit.setRuleCode("R-LOW");
                    hit.setRuleName("低权重规则");
                    hit.setWeight(10);
                    RuleResult result = new RuleResult();
                    result.setHits(List.of(hit));
                    return result;
                },
                data -> {
                    RuleHit hit = new RuleHit();
                    hit.setRuleCode("R-HIGH");
                    hit.setRuleName("高权重规则");
                    hit.setWeight(90);
                    RuleResult result = new RuleResult();
                    result.setHits(List.of(hit));
                    return result;
                }
        ));

        TenderReviewData data = new TenderReviewData();
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-001");
        scope.setDocumentIds(List.of("DOC-A", "DOC-B"));
        data.setCompareScopes(List.of(scope));

        RuleResult result = ruleEngine.execute(data);

        assertEquals(2, result.getHits().size());
        assertEquals("R-HIGH", result.getHits().get(0).getRuleCode());
        assertEquals("R-LOW", result.getHits().get(1).getRuleCode());
    }

    /**
     * 验证规则引擎在无执行器命中时返回空结果。
     */
    @Test
    void shouldReturnEmptyWhenNoExecutorProducesHits() {
        TenderRuleEngine ruleEngine = new TenderRuleEngine(List.of(data -> new RuleResult()));

        RuleResult result = ruleEngine.execute(new TenderReviewData());

        assertTrue(result.getHits().isEmpty());
    }
}
