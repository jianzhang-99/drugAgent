package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExemptionHit {

    private String hitId;
    private String ruleCode;
    private String ruleName;
    private String exemptionType;
    private String action;
    private String reason;
    private Integer beforeWeight;
    private Integer afterWeight;
}
