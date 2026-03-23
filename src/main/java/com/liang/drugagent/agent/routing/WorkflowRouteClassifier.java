package com.liang.drugagent.agent.routing;

import com.liang.drugagent.agent.util.CompletableFutureUtils;
import com.liang.drugagent.config.RoutingProperties;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.service.QwenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * WorkflowRouteClassifier - BaLian Model Router.
 *
 * <p>Encapsulates BaLian model calls for intent classification
 * when rules cannot make a decision.</p>
 *
 * <p>Responsibility:</p>
 * <ul>
 *   <li>Only performs intent classification</li>
 *   <li>Does not handle routing logic - delegates to caller</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Slf4j
@Service
public class WorkflowRouteClassifier {

    private static final double CONFIDENCE_THRESHOLD_ACCEPT = 0.75;
    private static final double CONFIDENCE_THRESHOLD_FALLBACK = 0.5;

    private final QwenService qwenService;
    private final RoutePromptBuilder promptBuilder;
    private final RouteDecisionParser parser;
    private final RouteDecisionValidator validator;
    private final RoutingProperties routingProperties;

    public WorkflowRouteClassifier(QwenService qwenService,
                                    RoutePromptBuilder promptBuilder,
                                    RouteDecisionParser parser,
                                    RouteDecisionValidator validator,
                                    RoutingProperties routingProperties) {
        this.qwenService = qwenService;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.validator = validator;
        this.routingProperties = routingProperties;
    }

    /**
     * Performs intent classification using BaLian model.
     *
     * <p>Call flow:</p>
     * <ol>
     *   <li>Build prompt</li>
     *   <li>Call Qwen model</li>
     *   <li>Parse JSON output</li>
     *   <li>Validate result</li>
     * </ol>
     *
     * @param req       Request object
     * @param fileNames List of file names (extracted from metadata)
     * @return Route decision result; null on timeout or exception
     * @throws RouteClassificationException On unrecoverable errors
     */
    public WorkflowRouteDecision classify(DrugAgentReq req, List<String> fileNames) throws RouteClassificationException {
        // 1. Build prompt
        String prompt = promptBuilder.buildPrompt(req.getQuery(), req.getFileIds(), fileNames);

        log.debug("[WorkflowRouteClassifier] Calling BaLian model, query={}, fileCount={}",
                req.getQuery(), req.getFileIds() == null ? 0 : req.getFileIds().size());

        // 2. Call model with timeout control
        String rawResponse;
        int timeoutMs = routingProperties.getLlmTimeout();
        try {
            rawResponse = callWithTimeout(prompt, timeoutMs);
        } catch (TimeoutException e) {
            log.error("[WorkflowRouteClassifier] BaLian model call timeout ({}ms)", timeoutMs, e);
            throw new RouteClassificationException("BaLian model call timeout", e);
        } catch (Exception e) {
            log.error("[WorkflowRouteClassifier] BaLian model call exception", e);
            throw new RouteClassificationException("BaLian model call exception: " + e.getMessage(), e);
        }

        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("[WorkflowRouteClassifier] BaLian model returned empty");
            return null;
        }

        log.debug("[WorkflowRouteClassifier] Raw model output: {}", rawResponse);

        // 3. Parse JSON
        WorkflowRouteDecision decision = parser.parse(rawResponse);
        if (decision == null) {
            log.warn("[WorkflowRouteClassifier] Model output parsing failed");
            return null;
        }

        // 4. Validate
        if (!validator.isValid(decision)) {
            log.warn("[WorkflowRouteClassifier] Model output validation failed, decision={}", decision);
            return null;
        }

        log.info("[WorkflowRouteClassifier] Classification complete, scene={}, confidence={}, reason={}",
                decision.getScene(), decision.getConfidence(), decision.getReason());

        return decision;
    }

    /**
     * Calls model with timeout control.
     */
    private String callWithTimeout(String prompt, long timeoutMs) throws TimeoutException {
        try {
            return CompletableFutureUtils.executeWithTimeout(() -> qwenService.chat(prompt), timeoutMs);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException("Call timeout");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Decides whether to accept LLM decision based on confidence.
     *
     * @param decision     LLM classification decision
     * @param ruleDecision Rule decision (can be null)
     * @return true if LLM decision should be accepted
     */
    public boolean shouldAccept(WorkflowRouteDecision decision, WorkflowRouteDecision ruleDecision) {
        if (decision == null) {
            return false;
        }

        Double confidence = decision.getConfidence();
        if (confidence == null) {
            return false;
        }

        // >= 0.75 accept directly
        if (confidence >= CONFIDENCE_THRESHOLD_ACCEPT) {
            return true;
        }

        // < 0.5 fallback to rule or UNKNOWN
        if (confidence < CONFIDENCE_THRESHOLD_FALLBACK) {
            return false;
        }

        // 0.5 - 0.75 range: if rule has high confidence decision, prefer rule
        if (ruleDecision != null && ruleDecision.getConfidence() != null && ruleDecision.getConfidence() >= 0.9) {
            log.debug("[WorkflowRouteClassifier] Confidence 0.5-0.75, but rule has high confidence decision, prefer rule");
            return false;
        }

        return true;
    }
}
