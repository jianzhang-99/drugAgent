package com.liang.drugagent.agent;

import com.liang.drugagent.agent.context.AgentContext;
import com.liang.drugagent.agent.context.IntentUnderstandingContext;
import com.liang.drugagent.agent.policy.ClarificationPolicy;
import com.liang.drugagent.agent.policy.DecisionMerger;
import com.liang.drugagent.config.RoutingProperties;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.resp.DrugAgentResp;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.domain.workflow.WorkflowResult;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.workflow.SceneWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 上层 Agent 统一调度器实现（Phase 2 升级版）。
 *
 * <p>完整流程：
 * <ol>
 *   <li>ContextEnricher 收集上下文</li>
 *   <li>RuleSignalProvider 提供规则信号</li>
 *   <li>IntentUnderstandingService 做 LLM 意图理解</li>
 *   <li>DecisionMerger 融合决策</li>
 *   <li>ClarificationPolicy 判断是否需要澄清</li>
 *   <li>WorkflowRegistry 分发执行</li>
 * </ol>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Slf4j
@Component
public class DefaultUpperAgentOrchestrator implements UpperAgentOrchestrator {

    private final ContextEnricher contextEnricher;
    private final RuleSignalProvider ruleSignalProvider;
    private final IntentUnderstandingService intentUnderstandingService;
    private final DecisionMerger decisionMerger;
    private final ClarificationPolicy clarificationPolicy;
    private final WorkflowRegistry workflowRegistry;
    private final RoutingProperties routingProperties;

    public DefaultUpperAgentOrchestrator(ContextEnricher contextEnricher,
                                          RuleSignalProvider ruleSignalProvider,
                                          IntentUnderstandingService intentUnderstandingService,
                                          DecisionMerger decisionMerger,
                                          ClarificationPolicy clarificationPolicy,
                                          WorkflowRegistry workflowRegistry,
                                          RoutingProperties routingProperties) {
        this.contextEnricher = contextEnricher;
        this.ruleSignalProvider = ruleSignalProvider;
        this.intentUnderstandingService = intentUnderstandingService;
        this.decisionMerger = decisionMerger;
        this.clarificationPolicy = clarificationPolicy;
        this.workflowRegistry = workflowRegistry;
        this.routingProperties = routingProperties;
    }

    @Override
    public DrugAgentResp handle(DrugAgentReq req) {
        AgentContext context = AgentContext.from(req);
        log.info("[UpperAgent] Start sync handling via Phase 2 pipeline: traceId={}, sessionId={}",
                context.getTraceId(), context.getSessionId());

        try {
            // Step 1: 收集上下文
            IntentUnderstandingContext intentContext = contextEnricher.enrich(req, context);
            context.setIntentContext(intentContext);

            // Step 2: 获取规则信号
            RuleSignalProvider.RuleSignals ruleSignals = ruleSignalProvider.provide(req, context);

            // Step 3: LLM 意图理解
            WorkflowRouteDecision llmDecision = intentUnderstandingService.understand(intentContext);

            // Step 4: 融合决策
            WorkflowRouteDecision finalDecision = decisionMerger.merge(llmDecision, ruleSignals);
            context.setSceneType(finalDecision.getScene());

            log.info("[UpperAgent] Final decision: traceId={}, scene={}, source={}, confidence={}, reason={}",
                    context.getTraceId(),
                    finalDecision.getScene(),
                    finalDecision.getSource(),
                    finalDecision.getConfidence(),
                    finalDecision.getReason());

            // Step 5: 判断是否需要澄清
            if (clarificationPolicy.shouldClarify(finalDecision)) {
                String question = clarificationPolicy.generateClarificationQuestion(finalDecision);
                WorkflowRouteDecision clarificationDecision = WorkflowRouteDecision.builder()
                        .scene(finalDecision.getScene())
                        .source(finalDecision.getSource())
                        .reason(finalDecision.getReason())
                        .confidence(finalDecision.getConfidence())
                        .requiresClarification(true)
                        .clarificationQuestion(question)
                        .raw(finalDecision.getRaw())
                        .build();
                log.info("[UpperAgent] Requiring clarification: traceId={}, question={}",
                        context.getTraceId(), question);
                return buildClarificationResponse(context, clarificationDecision);
            }

            // Step 6: 分发到 workflow 执行
            return executeWorkflow(context, finalDecision);

        } catch (IntentUnderstandingService.IntentUnderstandingException e) {
            log.error("[UpperAgent] Intent understanding failed: traceId={}, error={}",
                    context.getTraceId(), e.getMessage());
            // LLM 失败时回退到规则判断
            return handleFallback(req, context, e.getMessage());
        } catch (Exception e) {
            log.error("[UpperAgent] Unexpected error: traceId={}", context.getTraceId(), e);
            return handleFallback(req, context, e.getMessage());
        }
    }

    @Override
    public SseEmitter streamHandle(DrugAgentReq req) {
        AgentContext context = AgentContext.from(req);
        log.info("[UpperAgent] Start stream handling via Phase 2 pipeline: traceId={}",
                context.getTraceId());

        SseEmitter emitter = new SseEmitter(0L);

        try {
            // Step 1: 收集上下文
            IntentUnderstandingContext intentContext = contextEnricher.enrich(req, context);

            // Step 2: 获取规则信号
            RuleSignalProvider.RuleSignals ruleSignals = ruleSignalProvider.provide(req, context);

            // Step 3: LLM 意图理解
            WorkflowRouteDecision llmDecision = intentUnderstandingService.understand(intentContext);

            // Step 4: 融合决策
            WorkflowRouteDecision finalDecision = decisionMerger.merge(llmDecision, ruleSignals);
            context.setSceneType(finalDecision.getScene());

            // 发送元信息
            sendEvent(emitter, "meta", Map.of(
                    "traceId", context.getTraceId(),
                    "scene", finalDecision.getScene().name(),
                    "source", finalDecision.getSource(),
                    "confidence", finalDecision.getConfidence(),
                    "routeReason", finalDecision.getReason() != null ? finalDecision.getReason() : ""
            ));

            // Step 5: 判断是否需要澄清
            if (clarificationPolicy.shouldClarify(finalDecision)) {
                String question = clarificationPolicy.generateClarificationQuestion(finalDecision);
                sendEvent(emitter, "delta", question);
                sendEvent(emitter, "done", Map.of(
                        "requiresClarification", true,
                        "scene", finalDecision.getScene().name()
                ));
                emitter.complete();
                return emitter;
            }

            // Step 6: 发送 workflow 开始信号
            sendEvent(emitter, "workflow_start", Map.of(
                    "scene", finalDecision.getScene().name(),
                    "message", "场景已识别，开始执行工作流..."
            ));

            sendEvent(emitter, "done", Map.of(
                    "scene", finalDecision.getScene().name(),
                    "requiresStreamContinue", true
            ));
            emitter.complete();

        } catch (IntentUnderstandingService.IntentUnderstandingException e) {
            log.error("[UpperAgent] Stream intent understanding failed: traceId={}", context.getTraceId(), e);
            try {
                sendEvent(emitter, "error", "意图理解失败: " + e.getMessage());
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        } catch (Exception e) {
            log.error("[UpperAgent] Stream unexpected error: traceId={}", context.getTraceId(), e);
            try {
                sendEvent(emitter, "error", e.getMessage());
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @Override
    public WorkflowRouteDecision decide(DrugAgentReq req, AgentContext context) {
        // 用于 handleUploadedFiles 预判场景
        IntentUnderstandingContext intentContext = contextEnricher.enrich(req, context);
        RuleSignalProvider.RuleSignals ruleSignals = ruleSignalProvider.provide(req, context);

        try {
            WorkflowRouteDecision llmDecision = intentUnderstandingService.understand(intentContext);
            return decisionMerger.merge(llmDecision, ruleSignals);
        } catch (IntentUnderstandingService.IntentUnderstandingException e) {
            log.warn("[UpperAgent] decide fallback to rule signals: {}", e.getMessage());
            return RuleSignalProvider.RuleSignals.class.isInstance(ruleSignals)
                    ? buildFallbackDecision(ruleSignals)
                    : buildFallbackDecision("LLM失败: " + e.getMessage());
        }
    }

    /**
     * 执行 workflow 并构建响应。
     */
    private DrugAgentResp executeWorkflow(AgentContext context, WorkflowRouteDecision decision) {
        SceneWorkflow workflow = workflowRegistry.get(decision.getScene());
        WorkflowResult result = workflow.execute(context);

        log.info("[UpperAgent] Workflow executed: traceId={}, scene={}, riskLevel={}, stepCount={}",
                context.getTraceId(),
                result.getScene(),
                result.getRiskLevel(),
                result.getSteps() == null ? 0 : result.getSteps().size());

        return buildResponse(context, result, decision);
    }

    /**
     * 构建澄清响应。
     */
    private DrugAgentResp buildClarificationResponse(AgentContext context, WorkflowRouteDecision decision) {
        DrugAgentResp resp = new DrugAgentResp();
        resp.setTraceId(context.getTraceId());
        resp.setScene(decision.getScene().name());
        resp.setRouteReason(decision.getReason());
        resp.setRouteSource(decision.getSource());
        resp.setConfidence(decision.getConfidence());
        resp.setRequiresClarification(true);
        resp.setClarificationQuestion(decision.getClarificationQuestion());
        resp.setAnswer(decision.getClarificationQuestion());
        resp.setSummary("需要补充信息");
        return resp;
    }

    /**
     * 构建最终响应。
     */
    private DrugAgentResp buildResponse(AgentContext context, WorkflowResult result, WorkflowRouteDecision decision) {
        DrugAgentResp resp = new DrugAgentResp();
        resp.setTraceId(context.getTraceId());
        resp.setScene(result.getScene().name());
        resp.setRouteReason(decision.getReason());
        resp.setRouteSource(decision.getSource());
        resp.setConfidence(decision.getConfidence());
        resp.setSummary(buildSummary(result));
        resp.setAnswer(result.getAnswer());
        resp.setRiskLevel(result.getRiskLevel());
        resp.setScore(result.getScore() != null ? result.getScore() : 0);
        resp.setSteps(normalizeSteps(result.getSteps()));
        resp.setReport(result.getReport());
        resp.setEvidenceList(result.getEvidenceList());
        resp.setEvidenceGroups(result.getEvidenceGroups());
        return resp;
    }

    /**
     * 回退处理（LLM 异常时）。
     */
    private DrugAgentResp handleFallback(DrugAgentReq req, AgentContext context, String errorMsg) {
        log.warn("[UpperAgent] Falling back to rule-based decision: {}", errorMsg);

        RuleSignalProvider.RuleSignals ruleSignals = ruleSignalProvider.provide(req, context);
        if (ruleSignals != null && ruleSignals.hitScenes() != null) {
            WorkflowRouteDecision fallbackDecision = buildFallbackDecision(ruleSignals);
            context.setSceneType(fallbackDecision.getScene());
            return executeWorkflow(context, fallbackDecision);
        }

        // 规则也无法决策时，返回 UNKNOWN
        DrugAgentResp resp = new DrugAgentResp();
        resp.setTraceId(context.getTraceId());
        resp.setScene(SceneEnum.UNKNOWN.name());
        resp.setRouteReason("LLM和规则都无法决策: " + errorMsg);
        resp.setRouteSource("fallback");
        resp.setConfidence(0.0);
        resp.setAnswer("抱歉，系统暂时无法理解您的请求，请稍后再试或提供更详细的信息。");
        resp.setSummary("系统无法理解请求");
        return resp;
    }

    private WorkflowRouteDecision buildFallbackDecision(RuleSignalProvider.RuleSignals ruleSignals) {
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.valueOf(ruleSignals.hitScenes()))
                .source("rule_fallback")
                .reason("LLM失败，规则信号接管: " + ruleSignals.reason())
                .confidence(ruleSignals.confidence())
                .requiresClarification(false)
                .clarificationQuestion("")
                .raw(Map.of("fallbackReason", "llm_failure"))
                .build();
    }

    private WorkflowRouteDecision buildFallbackDecision(String reason) {
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.UNKNOWN)
                .source("fallback")
                .reason(reason)
                .confidence(0.0)
                .requiresClarification(false)
                .clarificationQuestion("")
                .build();
    }

    private String buildSummary(WorkflowResult result) {
        if (result.getReport() != null
                && result.getReport().getOverview() != null
                && result.getReport().getOverview().getSummary() != null) {
            return result.getReport().getOverview().getSummary();
        }
        return result.getAnswer();
    }

    private List<String> normalizeSteps(List<String> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }
}
