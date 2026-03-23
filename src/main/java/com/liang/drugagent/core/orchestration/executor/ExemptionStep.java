package com.liang.drugagent.core.orchestration.executor;

import com.liang.drugagent.scenes.tender_review.domain.model.ExemptionResult;
import com.liang.drugagent.scenes.tender_review.domain.model.RuleHit;
import com.liang.drugagent.scenes.tender_review.domain.model.TenderReviewData;

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
