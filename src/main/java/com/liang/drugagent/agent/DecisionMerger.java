package com.liang.drugagent.agent;

import com.liang.drugagent.domain.routing.WorkflowRouteDecision;

/**
 * 决策融合器接口。
 *
 * <p>融合 LLM 决策和规则信号，生成最终决策。
 * 遵循以下优先级：
 * <ol>
 *   <li>显式 sceneHint 直接采纳</li>
 *   <li>高置信规则信号增强 LLM 置信度</li>
 *   <li>LLM 主判断 + 规则信号辅助验证</li>
 *   <li>低置信或冲突时触发澄清</li>
 * </ol>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
public interface DecisionMerger {

    /**
     * 融合 LLM 决策与规则信号，生成最终决策。
     *
     * @param llmDecision LLM 决策结果（来自 IntentUnderstandingService）
     * @param ruleSignals 规则信号（来自 RuleSignalProvider）
     * @return 最终路由决策
     */
    WorkflowRouteDecision merge(WorkflowRouteDecision llmDecision, RuleSignalProvider.RuleSignals ruleSignals);
}
