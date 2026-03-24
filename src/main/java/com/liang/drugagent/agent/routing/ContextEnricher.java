package com.liang.drugagent.agent.routing;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.controller.request.agent.DrugAgentReq;

/**
 * 上下文补充器接口。
 *
 * <p>在决策前补充文件、会话、元数据等上下文信息，
 * 供上层 Agent 意图理解使用。</p>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
public interface ContextEnricher {

    /**
     * 补充上下文信息。
     *
     * @param req 请求对象
     * @param context Agent 上下文
     * @return 意图理解上下文
     */
    IntentUnderstandingContext enrich(DrugAgentReq req, AgentContext context);
}
