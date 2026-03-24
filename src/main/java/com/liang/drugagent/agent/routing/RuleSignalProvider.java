package com.liang.drugagent.agent.routing;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.controller.request.agent.DrugAgentReq;

/**
 * 规则信号提供者接口。
 *
 * <p>将现有规则逻辑封装为"信号"形式，
 * 作为上层 Agent 决策的辅助参考。</p>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
public interface RuleSignalProvider {

    /**
     * 提供规则信号。
     *
     * @param req 请求对象
     * @param context Agent 上下文
     * @return 规则信号映射
     */
    RuleSignals provide(DrugAgentReq req, AgentContext context);

    /**
     * 规则信号结果。
     *
     * @param hitScenes 规则命中的场景候选
     * @param confidence 规则置信度 (0.0 ~ 1.0)
     * @param reason 命中原因描述
     * @param keyIndicators 关键指标映射
     */
    record RuleSignals(
            String hitScenes,
            double confidence,
            String reason,
            java.util.Map<String, Object> keyIndicators
    ) {}
}
