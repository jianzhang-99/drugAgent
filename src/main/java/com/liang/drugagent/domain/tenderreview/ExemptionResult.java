package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ExemptionResult {

    private List<RuleHit> effectiveHits = new ArrayList<>();
    private List<ExemptionHit> exemptionHits = new ArrayList<>();
}
