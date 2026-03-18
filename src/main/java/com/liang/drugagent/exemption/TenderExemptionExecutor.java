package com.liang.drugagent.exemption;

import com.liang.drugagent.domain.tenderreview.ExemptionHit;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;

import java.util.Optional;

public interface TenderExemptionExecutor {

    Optional<ExemptionHit> apply(RuleHit hit, TenderReviewData data);
}
