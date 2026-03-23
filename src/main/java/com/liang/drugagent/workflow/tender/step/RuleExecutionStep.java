package com.liang.drugagent.workflow.tender.step;

import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;

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
