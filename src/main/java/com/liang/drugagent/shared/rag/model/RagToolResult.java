package com.liang.drugagent.shared.rag.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RagToolResult {
    RagDecision decision;
    String riskLevel;
    double score;
    List<String> strategyUsed;
    List<String> evidenceList;
    String summary;
    List<String> reasons;
}
