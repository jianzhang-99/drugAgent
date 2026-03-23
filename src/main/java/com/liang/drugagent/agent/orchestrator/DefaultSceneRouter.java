package com.liang.drugagent.agent.orchestrator;

import com.liang.drugagent.agent.context.AgentContext;
import com.liang.drugagent.agent.routing.RouteClassificationException;
import com.liang.drugagent.agent.routing.RouteDecisionValidator;
import com.liang.drugagent.agent.routing.RuleBasedRouteDecider;
import com.liang.drugagent.agent.routing.WorkflowRouteClassifier;
import com.liang.drugagent.config.RoutingProperties;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.enums.SceneEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 场景路由器实现。
 *
 * <p>判断请求属于哪个业务场景。</p>
 *
 * <p>提供两种路由方式：</p>
 * <ul>
 *   <li>{@link #decide(DrugAgentReq, AgentContext)} - 返回完整决策信息</li>
 *   <li>{@link #route(DrugAgentReq, AgentContext)} - 兼容现有调用方，仅返回场景枚举</li>
 * </ul>
 *
 * <p>路由策略（优先级从高到低）：</p>
 * <ol>
 *   <li>前端显式指定场景（sceneHint）</li>
 *   <li>规则引擎判断（RuleBasedRouteDecider）</li>
 *   <li>百炼模型分类（WorkflowRouteClassifier）- 规则无法决策时兜底</li>
 *   <li>UNKNOWN 兜底</li>
 * </ol>
 *
 * @author liangjiajian
 */
@Slf4j
@Component
public class DefaultSceneRouter implements SceneRouter {

    private final RuleBasedRouteDecider ruleBasedRouteDecider;
    private final WorkflowRouteClassifier workflowRouteClassifier;
    private final RouteDecisionValidator validator;
    private final RoutingProperties routingProperties;

    public DefaultSceneRouter(RuleBasedRouteDecider ruleBasedRouteDecider,
                              WorkflowRouteClassifier workflowRouteClassifier,
                              RouteDecisionValidator validator,
                              RoutingProperties routingProperties) {
        this.ruleBasedRouteDecider = ruleBasedRouteDecider;
        this.workflowRouteClassifier = workflowRouteClassifier;
        this.validator = validator;
        this.routingProperties = routingProperties;
    }

    /**
     * 执行完整的路由决策。
     *
     * <p>决策流程：</p>
     * <ol>
     *   <li>规则优先决策</li>
     *   <li>规则无法决策时，如果 llm-enabled=true 则调用百炼模型分类</li>
     *   <li>百炼置信度 >= confidence-threshold 时采纳</li>
     *   <li>百炼置信度 < confidence-low-threshold 时回退到 UNKNOWN</li>
     *   <li>百炼置信度在两者区间时，结合规则二次判断</li>
     * </ol>
     *
     * @param req      请求对象
     * @param context  Agent 上下文
     * @return 路由决策结果
     */
    @Override
    public WorkflowRouteDecision decide(DrugAgentReq req, AgentContext context) {
        SceneEnum sceneHint = SceneEnum.fromHint(req.getSceneHint());
        double confidenceThreshold = routingProperties.getConfidenceThreshold();
        double confidenceLowThreshold = routingProperties.getConfidenceLowThreshold();

        // 1. 规则决策优先
        WorkflowRouteDecision ruleDecision = ruleBasedRouteDecider.decide(req, sceneHint);

        if (ruleDecision != null) {
            log.info("[SceneRouter] 规则决策命中, source={}, scene={}, confidence={}",
                    ruleDecision.getSource(), ruleDecision.getScene(), ruleDecision.getConfidence());
            syncToContext(context, ruleDecision);
            return ruleDecision;
        }

        // 2. 检查 LLM 是否启用
        if (!routingProperties.isLlmEnabled()) {
            log.info("[SceneRouter] 百炼路由已禁用，直接回退到 UNKNOWN");
            return buildFallbackDecision(context, "百炼路由已禁用");
        }

        // 3. 规则无法决策时，调用百炼模型分类
        log.debug("[SceneRouter] 规则无法决策，调用百炼模型分类");
        WorkflowRouteDecision llmDecision;
        try {
            List<String> fileNames = extractFileNames(req);
            llmDecision = workflowRouteClassifier.classify(req, fileNames);
        } catch (RouteClassificationException e) {
            log.warn("[SceneRouter] 百炼分类异常(source=fallback)，回退到 UNKNOWN", e);
            return buildFallbackDecision(context, "百炼分类异常: " + e.getMessage());
        }

        if (llmDecision == null) {
            log.warn("[SceneRouter] 百炼分类返回为空(source=fallback)，回退到 UNKNOWN");
            return buildFallbackDecision(context, "百炼分类返回为空");
        }

        // 4. 置信度阈值判断
        Double confidence = llmDecision.getConfidence();

        // >= confidenceThreshold 直接采纳
        if (validator.isConfidenceAboveThreshold(confidence, confidenceThreshold)) {
            log.info("[SceneRouter] 百炼分类置信度 >= {}, source=llm, scene={}, confidence={}",
                    confidenceThreshold, llmDecision.getScene(), confidence);
            syncToContext(context, llmDecision);
            return llmDecision;
        }

        // < confidenceLowThreshold 回退到 UNKNOWN
        if (validator.isConfidenceBelowFallback(confidence, confidenceLowThreshold)) {
            log.info("[SceneRouter] 百炼分类置信度 < {}, source=fallback, scene=UNKNOWN, confidence={}",
                    confidenceLowThreshold, confidence);
            return buildFallbackDecision(context, "百炼分类置信度过低: " + confidence);
        }

        // 5. 区间值，结合规则二次判断
        log.debug("[SceneRouter] 百炼分类置信度在 {}-{} 区间，结合规则二次判断, confidence={}",
                confidenceLowThreshold, confidenceThreshold, confidence);
        WorkflowRouteDecision secondRuleDecision = ruleBasedRouteDecider.decide(req, sceneHint);

        if (secondRuleDecision != null && secondRuleDecision.getConfidence() != null
                && secondRuleDecision.getConfidence() >= 0.7) {
            log.info("[SceneRouter] 二次规则判断有效, source=rule, scene={}, confidence={}",
                    secondRuleDecision.getScene(), secondRuleDecision.getConfidence());
            syncToContext(context, secondRuleDecision);
            return secondRuleDecision;
        }

        // 规则二次判断无效或置信度不足，采纳 LLM 决策
        log.info("[SceneRouter] 采纳百炼分类决策, source=llm, scene={}, confidence={}",
                llmDecision.getScene(), confidence);
        syncToContext(context, llmDecision);
        return llmDecision;
    }

    /**
     * 兼容现有调用方的路由方法。
     *
     * @param req      请求对象
     * @param context  Agent 上下文
     * @return 路由到的场景枚举
     */
    @Override
    public SceneEnum route(DrugAgentReq req, AgentContext context) {
        WorkflowRouteDecision decision = decide(req, context);
        return decision.getScene();
    }

    /**
     * 从请求中提取文件名列表。
     */
    @SuppressWarnings("unchecked")
    private List<String> extractFileNames(DrugAgentReq req) {
        Map<String, Object> metadata = req.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }
        Object rawFiles = metadata.get("uploadedFiles");
        if (!(rawFiles instanceof List<?> files)) {
            return List.of();
        }
        return files.stream()
                .filter(f -> f instanceof Map)
                .map(f -> (Map<?, ?>) f)
                .map(f -> {
                    Object name = f.get("filename");
                    return name == null ? "" : name.toString();
                })
                .toList();
    }

    /**
     * 将决策结果同步到 AgentContext。
     */
    private void syncToContext(AgentContext context, WorkflowRouteDecision decision) {
        context.getAttributes().put("routeSource", decision.getSource());
        context.getAttributes().put("routeReason", decision.getReason());
        context.getAttributes().put("routeConfidence", decision.getConfidence());
        context.setSceneType(decision.getScene());
    }

    /**
     * 构建兜底决策。
     */
    private WorkflowRouteDecision buildFallbackDecision(AgentContext context, String reason) {
        WorkflowRouteDecision fallback = WorkflowRouteDecision.builder()
                .scene(SceneEnum.UNKNOWN)
                .source("fallback")
                .reason(reason)
                .confidence(0.0)
                .requiresClarification(false)
                .build();
        syncToContext(context, fallback);
        return fallback;
    }
}
