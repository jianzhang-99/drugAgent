package com.liang.drugagent.workflow.tender.step;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.RiskFusionResult;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;

import java.util.List;

/**
 * 风险融合步骤。
 *
 * @author liangjiajian
 */
public interface RiskFusionStep {

    /**
     * 融合风险评分。
     */
    RiskFusionResult fuse(TenderReviewData tenderReviewData,
                         List<RuleHit> effectiveHits,
                         List<ExemptionHit> exemptionHits);
}
