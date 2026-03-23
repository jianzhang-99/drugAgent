package com.liang.drugagent.workflow.tender.step;

import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.engine.TenderRuleEngine;
import org.springframework.stereotype.Component;

/**
 * 规则执行步骤实现。
 *
 * @author liangjiajian
 */
@Component
public class RuleExecutionStepImpl implements RuleExecutionStep {

    private final TenderRuleEngine tenderRuleEngine;

    public RuleExecutionStepImpl(TenderRuleEngine tenderRuleEngine) {
        this.tenderRuleEngine = tenderRuleEngine;
    }

    @Override
    public RuleResult execute(TenderReviewData tenderReviewData) {
        return tenderRuleEngine.execute(tenderReviewData);
    }
}
