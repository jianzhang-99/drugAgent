package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RiskFusionResult {

    private String riskLevel;
    private Integer score;
    private String summary;
    private List<String> reasonCodes = new ArrayList<>();
}
