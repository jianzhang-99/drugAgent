package com.liang.drugagent.service.rag;

import lombok.Builder;
import lombok.Getter;

/**
 * 场景化 RAG 策略。
 */
@Getter
@Builder
public class RagPolicy {

    private int topK;
    private int keywordTopN;
    private int vectorTopN;
    private double keywordWeight;
    private double vectorWeight;
    private boolean useRrf;
    private int rrfK;
    private double minScore;
    private int minEvidence;
    private String riskLevel;
}
