package com.liang.drugagent.scene.tender_review.support.executor;

import com.liang.drugagent.scene.tender_review.model.ExemptionHit;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;

import java.util.Optional;

public interface TenderExemptionExecutor {

    Optional<ExemptionHit> apply(RuleHit hit, TenderReviewData data);
}
