package com.liang.drugagent.core.orchestration.executor;

import com.liang.drugagent.scenes.tender_review.domain.model.RuleResult;
import com.liang.drugagent.scenes.tender_review.domain.model.TenderReviewData;
import com.liang.drugagent.scenes.tender_review.domain.service.TenderRuleEngine;
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
