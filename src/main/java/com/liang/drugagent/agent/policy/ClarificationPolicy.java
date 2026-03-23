package com.liang.drugagent.agent.policy;

import com.liang.drugagent.domain.routing.WorkflowRouteDecision;

/**
 * 澄清策略接口。
 *
 * <p>控制低置信请求的补问策略。
 * 当置信度低于阈值或需要澄清时，决定如何与用户交互。</p>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
public interface ClarificationPolicy {

    /**
     * 判断是否需要澄清。
     *
     * @param decision 当前决策
     * @return true 如果需要向用户澄清
     */
    boolean shouldClarify(WorkflowRouteDecision decision);

    /**
     * 生成澄清问题。
     *
     * @param decision 当前决策
     * @return 澄清问题文本
     */
    String generateClarificationQuestion(WorkflowRouteDecision decision);
}
