package com.liang.drugagent.workflow.tender.step;

import com.liang.drugagent.domain.tenderreview.ExemptionResult;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;

import java.util.List;

/**
 * 误报豁免步骤。
 *
 * @author liangjiajian
 */
public interface ExemptionStep {

    /**
     * 应用误报豁免逻辑。
     */
    ExemptionResult apply(List<RuleHit> hits, TenderReviewData tenderReviewData);
}
