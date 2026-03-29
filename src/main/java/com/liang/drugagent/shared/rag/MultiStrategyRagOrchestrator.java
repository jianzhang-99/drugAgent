package com.liang.drugagent.shared.rag;

import com.liang.drugagent.shared.rag.model.RagDecision;
import com.liang.drugagent.shared.rag.model.RagQueryContext;
import com.liang.drugagent.shared.rag.model.RagStrategyResult;
import com.liang.drugagent.shared.rag.model.RagToolResult;
import com.liang.drugagent.shared.rag.strategy.RagStrategy;
import com.liang.drugagent.shared.rag.strategy.RagStrategyRouter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MultiStrategyRagOrchestrator {

    private final RagStrategyRouter strategyRouter;
    private final Map<String, RagStrategy> strategyRegistry;
    private final RagScoreFusionService scoreFusionService;
    private final RagDecisionGate decisionGate;
    private final RagTraceLogger traceLogger;

    public MultiStrategyRagOrchestrator(RagStrategyRouter strategyRouter,
                                        List<RagStrategy> strategies,
                                        RagScoreFusionService scoreFusionService,
                                        RagDecisionGate decisionGate,
                                        RagTraceLogger traceLogger) {
        this.strategyRouter = strategyRouter;
        this.scoreFusionService = scoreFusionService;
        this.decisionGate = decisionGate;
        this.traceLogger = traceLogger;
        this.strategyRegistry = new HashMap<>();
        for (RagStrategy strategy : strategies) {
            this.strategyRegistry.put(strategy.strategyCode(), strategy);
        }
    }

    public RagToolResult execute(RagQueryContext context) {
        List<String> strategyCodes = strategyRouter.route(context);
        traceLogger.logSelectedStrategies(context, strategyCodes);

        List<RagStrategyResult> results = new ArrayList<>();
        for (String code : strategyCodes) {
            RagStrategy strategy = strategyRegistry.get(code);
            if (strategy == null || !strategy.support(context)) {
                continue;
            }
            results.add(strategy.execute(context));
        }

        traceLogger.logStrategyResults(results);
        RagScoreFusionService.FusionResult fusion = scoreFusionService.fuse(results);
        List<String> evidences = mergeEvidences(results);
        RagDecision decision = decisionGate.decide(fusion.getFinalScore(), evidences.size());

        List<String> usedStrategies = results.stream().map(RagStrategyResult::getStrategyCode).distinct().toList();
        String summary = buildSummary(results, decision, fusion.getRiskLevel());
        List<String> reasons = buildReasons(results, fusion, evidences.size(), decision);

        traceLogger.logFinalDecision(fusion.getFinalScore(), fusion.getRiskLevel(), decision, summary, reasons);

        return RagToolResult.builder()
                .decision(decision)
                .riskLevel(fusion.getRiskLevel())
                .score(fusion.getFinalScore())
                .strategyUsed(usedStrategies)
                .evidenceList(evidences)
                .summary(summary)
                .reasons(reasons)
                .build();
    }

    private List<String> mergeEvidences(List<RagStrategyResult> results) {
        Set<String> merged = new LinkedHashSet<>();
        for (RagStrategyResult result : results) {
            if (result.getEvidences() != null) {
                merged.addAll(result.getEvidences());
            }
        }
        return new ArrayList<>(merged);
    }

    private String buildSummary(List<RagStrategyResult> results, RagDecision decision, String riskLevel) {
        if (results == null || results.isEmpty()) {
            return "未命中可执行策略";
        }
        RagStrategyResult top = results.stream().max(Comparator.comparingDouble(RagStrategyResult::getScore)).orElse(results.getFirst());
        return "决策=" + decision + "，风险=" + riskLevel + "，主导策略=" + top.getStrategyCode() + "，结论=" + top.getSummary();
    }

    private List<String> buildReasons(List<RagStrategyResult> results,
                                      RagScoreFusionService.FusionResult fusion,
                                      int evidenceCount,
                                      RagDecision decision) {
        List<String> reasons = new ArrayList<>();
        reasons.add("strategyCount=" + (results == null ? 0 : results.size()));
        reasons.add("finalScore=" + String.format("%.2f", fusion.getFinalScore()));
        reasons.add("riskLevel=" + fusion.getRiskLevel());
        reasons.add("evidenceCount=" + evidenceCount);
        reasons.add("decision=" + decision.name());
        if (results != null && !results.isEmpty()) {
            String detail = results.stream()
                    .map(it -> it.getStrategyCode() + "=" + String.format("%.1f", it.getScore()))
                    .collect(Collectors.joining(", "));
            reasons.add("strategyScores=" + detail);
        }
        return reasons;
    }
}
