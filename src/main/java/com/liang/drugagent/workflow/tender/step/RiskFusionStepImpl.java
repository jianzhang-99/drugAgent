package com.liang.drugagent.workflow.tender.step;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.RiskFusionResult;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.service.tenderreview.RiskFusionService;
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
