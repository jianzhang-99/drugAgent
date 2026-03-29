package com.liang.drugagent.shared.rag.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RagStrategyResult {
    String strategyCode;
    double score;
    String riskLevel;
    List<String> evidences;
    boolean passedLocalGate;
    String summary;
}
