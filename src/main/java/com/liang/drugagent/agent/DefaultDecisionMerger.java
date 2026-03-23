package com.liang.drugagent.agent;

import com.liang.drugagent.config.RoutingProperties;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.enums.SceneEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认决策融合器实现。
 *
 * <p>融合规则信号与 LLM 决策：
 * <ol>
 *   <li>规则高置信（>=0.9）时，以规则为主</li>
 *   <li>LLM 置信度高（>=0.75）时，以 LLM 为主</li>
 *   <li>双方置信度都在区间时，取较高者</li>
 *   <li>冲突时需要澄清</li>
 * </ol>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Slf4j
@Component
public class DefaultDecisionMerger implements DecisionMerger {

    private final RoutingProperties routingProperties;

    public DefaultDecisionMerger(RoutingProperties routingProperties) {
        this.routingProperties = routingProperties;
    }

    @Override
    public WorkflowRouteDecision merge(WorkflowRouteDecision llmDecision, RuleSignalProvider.RuleSignals ruleSignals) {
        // Case 1: sceneHint 已在 LLM decision 中标记为 source=sceneHint，直接采纳
        if (llmDecision != null && "sceneHint".equals(llmDecision.getSource())) {
            log.info("[DecisionMerger] SceneHint explicit, adopting: scene={}", llmDecision.getScene());
            return llmDecision;
        }

        // Case 2: 规则无信号时，直接采纳 LLM 决策
        if (ruleSignals == null || ruleSignals.confidence() <= 0.0 || ruleSignals.hitScenes() == null) {
            log.info("[DecisionMerger] No rule signal, adopting LLM decision: scene={}, confidence={}",
                    llmDecision != null ? llmDecision.getScene() : "null",
                    llmDecision != null ? llmDecision.getConfidence() : 0.0);
            return llmDecision != null ? llmDecision : buildUnknownDecision("LLM 和规则都无法决策");
        }

        // Case 3: LLM 无决策时，采用规则信号
        if (llmDecision == null) {
            log.info("[DecisionMerger] No LLM decision, adopting rule signal: scene={}, confidence={}",
                    ruleSignals.hitScenes(), ruleSignals.confidence());
            return buildDecisionFromRuleSignals(ruleSignals);
        }

        // Case 4: 双方都有决策，进行融合
        return mergeDecisions(llmDecision, ruleSignals);
    }

    private WorkflowRouteDecision mergeDecisions(WorkflowRouteDecision llmDecision, RuleSignalProvider.RuleSignals ruleSignals) {
        String llmScene = llmDecision.getScene().name();
        String ruleScene = ruleSignals.hitScenes();
        double llmConfidence = llmDecision.getConfidence() != null ? llmDecision.getConfidence() : 0.5;
        double ruleConfidence = ruleSignals.confidence();

        log.debug("[DecisionMerger] Merging: LLM(scene={}, conf={}) vs Rule(scene={}, conf={})",
                llmScene, llmConfidence, ruleScene, ruleConfidence);

        // 场景一致时，融合置信度
        if (llmScene.equals(ruleScene)) {
            double mergedConfidence = Math.max(llmConfidence, ruleConfidence);
            // 如果双方都认为同一个场景，取较高置信度并标记为融合决策
            Map<String, Object> raw = new HashMap<>(llmDecision.getRaw() != null ? llmDecision.getRaw() : new HashMap<>());
            raw.put("ruleSignalSource", ruleSignals.reason());
            raw.put("mergedFrom", "llm+rule");
            raw.put("originalLlmConfidence", llmConfidence);
            raw.put("originalRuleConfidence", ruleConfidence);

            log.info("[DecisionMerger] Scenes match, merging: scene={}, finalConfidence={}",
                    llmScene, mergedConfidence);

            return WorkflowRouteDecision.builder()
                    .scene(llmDecision.getScene())
                    .source("upper_agent_merged")
                    .reason(String.format("LLM与规则信号一致：%s；规则信号：%s",
                            llmDecision.getReason(), ruleSignals.reason()))
                    .confidence(mergedConfidence)
                    .requiresClarification(false)
                    .clarificationQuestion("")
                    .raw(raw)
                    .build();
        }

        // 场景冲突时
        log.warn("[DecisionMerger] Scene conflict: LLM={}, Rule={}", llmScene, ruleScene);

        // 规则高置信时优先规则
        if (ruleConfidence >= routingProperties.getConfidenceThreshold()) {
            Map<String, Object> raw = new HashMap<>(llmDecision.getRaw() != null ? llmDecision.getRaw() : new HashMap<>());
            raw.put("ruleSignalSource", ruleSignals.reason());
            raw.put("conflictResolvedBy", "rule");
            raw.put("llmScene", llmScene);
            raw.put("ruleScene", ruleScene);

            log.info("[DecisionMerger] Conflict resolved by rule: scene={}", ruleScene);

            return WorkflowRouteDecision.builder()
                    .scene(SceneEnum.valueOf(ruleScene))
                    .source("rule_override")
                    .reason(String.format("规则信号置信度高(%s)，覆盖LLM判断。%s",
                            formatConfidence(ruleConfidence), ruleSignals.reason()))
                    .confidence(ruleConfidence)
                    .requiresClarification(false)
                    .clarificationQuestion("")
                    .raw(raw)
                    .build();
        }

        // LLM 高置信时优先 LLM
        if (llmConfidence >= routingProperties.getConfidenceThreshold()) {
            log.info("[DecisionMerger] Conflict resolved by LLM: scene={}", llmScene);
            return llmDecision;
        }

        // 双方置信度都较低时，需要澄清
        log.info("[DecisionMerger] Low confidence conflict, requiring clarification");

        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.UNKNOWN)
                .source("conflict")
                .reason("LLM 和规则信号场景冲突且置信度都不高")
                .confidence(Math.min(llmConfidence, ruleConfidence))
                .requiresClarification(true)
                .clarificationQuestion("系统检测到您可能想要执行多个操作。请确认您是想要：1）标书查重分析；2）合同预审；还是3）风险预警分析？")
                .raw(Map.of(
                        "llmScene", llmScene,
                        "llmConfidence", llmConfidence,
                        "ruleScene", ruleScene,
                        "ruleConfidence", ruleConfidence,
                        "conflictType", "scene_mismatch"
                ))
                .build();
    }

    private WorkflowRouteDecision buildDecisionFromRuleSignals(RuleSignalProvider.RuleSignals ruleSignals) {
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.valueOf(ruleSignals.hitScenes()))
                .source("rule")
                .reason(ruleSignals.reason())
                .confidence(ruleSignals.confidence())
                .requiresClarification(false)
                .clarificationQuestion("")
                .raw(Map.of("ruleSignalSource", ruleSignals.reason()))
                .build();
    }

    private WorkflowRouteDecision buildUnknownDecision(String reason) {
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.UNKNOWN)
                .source("fallback")
                .reason(reason)
                .confidence(0.0)
                .requiresClarification(false)
                .clarificationQuestion("")
                .build();
    }

    private String formatConfidence(double confidence) {
        return String.format("%.2f", confidence);
    }
}
