package com.liang.drugagent.core.orchestration.executor;

import com.liang.drugagent.scenes.tender_review.domain.model.RuleResult;
import com.liang.drugagent.scenes.tender_review.domain.model.TenderReviewData;

/**
 * 规则执行步骤。
 *
 * @author liangjiajian
 */
public interface RuleExecutionStep {

    /**
     * 执行规则扫描。
     */
    RuleResult execute(TenderReviewData tenderReviewData);
}
