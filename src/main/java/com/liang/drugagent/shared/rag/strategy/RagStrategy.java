package com.liang.drugagent.shared.rag.strategy;

import com.liang.drugagent.shared.rag.model.RagQueryContext;
import com.liang.drugagent.shared.rag.model.RagStrategyResult;

public interface RagStrategy {
    String strategyCode();
    boolean support(RagQueryContext context);
    RagStrategyResult execute(RagQueryContext context);
}
