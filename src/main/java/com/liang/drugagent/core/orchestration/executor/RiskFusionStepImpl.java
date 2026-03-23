package com.liang.drugagent.core.orchestration.executor;

import com.liang.drugagent.scenes.tender_review.domain.model.ExemptionHit;
import com.liang.drugagent.scenes.tender_review.domain.model.RiskFusionResult;
import com.liang.drugagent.scenes.tender_review.domain.model.RuleHit;
import com.liang.drugagent.scenes.tender_review.domain.model.TenderReviewData;
import com.liang.drugagent.scenes.tender_review.application.services.RiskFusionService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 风险融合步骤实现。
 *
 * @author liangjiajian
 */
@Component
public class RiskFusionStepImpl implements RiskFusionStep {

    private final RiskFusionService riskFusionService;

    public RiskFusionStepImpl(RiskFusionService riskFusionService) {
        this.riskFusionService = riskFusionService;
    }

    @Override
    public RiskFusionResult fuse(TenderReviewData tenderReviewData,
                                List<RuleHit> effectiveHits,
                                List<ExemptionHit> exemptionHits) {
        return riskFusionService.fuse(tenderReviewData, effectiveHits, exemptionHits);
    }
}
