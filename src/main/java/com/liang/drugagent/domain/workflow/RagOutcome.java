package com.liang.drugagent.domain.workflow;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Workflow 内部 RAG 结果。
 */
@Getter
@Setter
public class RagOutcome {

    private String answer;
    private String decision;
    private String reason;
    private String riskLevel;
    private List<EvidenceItem> evidenceList = new ArrayList<>();
}
