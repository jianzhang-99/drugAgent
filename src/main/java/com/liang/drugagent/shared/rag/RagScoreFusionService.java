package com.liang.drugagent.shared.rag;

import com.liang.drugagent.shared.rag.model.RagStrategyResult;
import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagScoreFusionService {

    public FusionResult fuse(List<RagStrategyResult> strategyResults) {
        if (strategyResults == null || strategyResults.isEmpty()) {
            return FusionResult.builder().finalScore(0D).riskLevel("LOW").maxScore(0D).avgScore(0D).build();
        }
        double max = strategyResults.stream().mapToDouble(RagStrategyResult::getScore).max().orElse(0D);
        double avg = strategyResults.stream().mapToDouble(RagStrategyResult::getScore).average().orElse(0D);
        double finalScore = 0.6D * max + 0.4D * avg;
        return FusionResult.builder()
                .finalScore(finalScore)
                .riskLevel(toRiskLevel(finalScore))
                .maxScore(max)
                .avgScore(avg)
                .build();
    }

    public String toRiskLevel(double score) {
        if (score >= 75D) {
            return "HIGH";
        }
        if (score >= 45D) {
            return "MEDIUM";
        }
        return "LOW";
    }

    @Value
    @Builder
    public static class FusionResult {
        double finalScore;
        String riskLevel;
        double maxScore;
        double avgScore;
    }
}
