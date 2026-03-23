package com.liang.drugagent.core.orchestration.executor;

import com.liang.drugagent.scenes.tender_review.domain.model.ExemptionHit;
import com.liang.drugagent.scenes.tender_review.domain.model.RiskFusionResult;
import com.liang.drugagent.scenes.tender_review.domain.model.RuleHit;
import com.liang.drugagent.scenes.tender_review.domain.model.TenderReviewData;

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
