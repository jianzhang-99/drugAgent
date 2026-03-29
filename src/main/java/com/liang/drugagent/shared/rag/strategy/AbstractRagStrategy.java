package com.liang.drugagent.shared.rag.strategy;

import com.liang.drugagent.shared.rag.model.RagQueryContext;
import com.liang.drugagent.shared.rag.model.RagStrategyResult;

import java.util.List;

public abstract class AbstractRagStrategy implements RagStrategy {

    @Override
    public boolean support(RagQueryContext context) {
        return context != null && context.getScene() != null;
    }

    protected RagStrategyResult buildSimpleResult(double score,
                                                  String riskLevel,
                                                  List<String> evidences,
                                                  String summary) {
        boolean pass = evidences != null && !evidences.isEmpty() && score >= 45D;
        return RagStrategyResult.builder()
                .strategyCode(strategyCode())
                .score(score)
                .riskLevel(riskLevel)
                .evidences(evidences == null ? List.of() : evidences)
                .passedLocalGate(pass)
                .summary(summary)
                .build();
    }
}
