package com.liang.drugagent.workflow.tender.step;

import com.liang.drugagent.domain.tenderreview.ExemptionResult;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.engine.TenderExemptionEngine;
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
