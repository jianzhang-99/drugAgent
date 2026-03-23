package com.liang.drugagent.core.orchestration.executor;

import com.liang.drugagent.scenes.tender_review.domain.model.ExemptionResult;
import com.liang.drugagent.scenes.tender_review.domain.model.RuleHit;
import com.liang.drugagent.scenes.tender_review.domain.model.TenderReviewData;
import com.liang.drugagent.scenes.tender_review.domain.service.TenderExemptionEngine;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 误报豁免步骤实现。
 *
 * @author liangjiajian
 */
@Component
public class ExemptionStepImpl implements ExemptionStep {

    private final TenderExemptionEngine tenderExemptionEngine;

    public ExemptionStepImpl(TenderExemptionEngine tenderExemptionEngine) {
        this.tenderExemptionEngine = tenderExemptionEngine;
    }

    @Override
    public ExemptionResult apply(List<RuleHit> hits, TenderReviewData tenderReviewData) {
        return tenderExemptionEngine.apply(hits, tenderReviewData);
    }
}
