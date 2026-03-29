package com.liang.drugagent.shared.rag;

import com.liang.drugagent.shared.rag.model.RagDecision;
import com.liang.drugagent.shared.rag.model.RagQueryContext;
import com.liang.drugagent.shared.rag.model.RagStrategyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RagTraceLogger {

    public void logSelectedStrategies(RagQueryContext context, List<String> strategyCodes) {
        log.info("[rag-trace] scene={}, sessionId={}, selectedStrategies={}",
                context == null || context.getScene() == null ? null : context.getScene().name(),
                context == null ? null : context.getSessionId(),
                strategyCodes);
    }

    public void logStrategyResults(List<RagStrategyResult> strategyResults) {
        if (strategyResults == null || strategyResults.isEmpty()) {
            log.info("[rag-trace] strategyResults=empty");
            return;
        }
        for (RagStrategyResult result : strategyResults) {
            int evidenceCount = result.getEvidences() == null ? 0 : result.getEvidences().size();
            log.info("[rag-trace] strategy={}, score={}, riskLevel={}, evidenceCount={}, passedLocalGate={}, summary={}",
                    result.getStrategyCode(), result.getScore(), result.getRiskLevel(), evidenceCount, result.isPassedLocalGate(), result.getSummary());
        }
    }

    public void logFinalDecision(double finalScore, String riskLevel, RagDecision decision, String summary, List<String> reasons) {
        log.info("[rag-trace] finalScore={}, riskLevel={}, decision={}, summary={}, reasons={}",
                finalScore, riskLevel, decision, summary, reasons);
    }
}
