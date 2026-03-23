package com.liang.drugagent.scenes.tender_review.domain.service.exemption;

import com.liang.drugagent.scenes.tender_review.domain.model.ExemptionHit;
import com.liang.drugagent.scenes.tender_review.domain.model.RuleHit;
import com.liang.drugagent.scenes.tender_review.domain.model.TenderReviewData;

import java.util.Optional;

public interface TenderExemptionExecutor {

    Optional<ExemptionHit> apply(RuleHit hit, TenderReviewData data);
}
