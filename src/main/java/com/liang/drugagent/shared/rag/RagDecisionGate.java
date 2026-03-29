package com.liang.drugagent.shared.rag;

import com.liang.drugagent.shared.rag.model.RagDecision;
import org.springframework.stereotype.Component;

@Component
public class RagDecisionGate {

    private static final int DEFAULT_MIN_EVIDENCE = 2;
    private static final double DEFAULT_MIN_SCORE = 45D;

    public RagDecision decide(double finalScore, int evidenceCount) {
        return decide(finalScore, evidenceCount, DEFAULT_MIN_SCORE, DEFAULT_MIN_EVIDENCE);
    }

    public RagDecision decide(double finalScore, int evidenceCount, double minScore, int minEvidence) {
        if (finalScore < minScore) {
            return RagDecision.NO_HIT;
        }
        if (evidenceCount < minEvidence) {
            return RagDecision.NEED_HUMAN_REVIEW;
        }
        return RagDecision.ANSWERED;
    }
}
