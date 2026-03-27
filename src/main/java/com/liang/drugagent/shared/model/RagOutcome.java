package com.liang.drugagent.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Workflow 内部 RAG 结果。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagOutcome {

    private String answer;
    private String decision;
    private String reason;
    private String riskLevel;
    private List<EvidenceItem> evidenceList = new ArrayList<>();
}
