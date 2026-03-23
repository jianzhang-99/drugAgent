package com.liang.drugagent.agent.orchestrator;

import com.liang.drugagent.agent.context.AgentContext;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.enums.SceneEnum;

/**
 * 场景路由器接口。
 *
 * <p>负责判断请求属于哪个业务场景。</p>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
public interface SceneRouter {

    /**
     * 执行完整的路由决策。
     *
     * @param req      请求对象
     * @param context  Agent 上下文
     * @return 路由决策结果
     */
    WorkflowRouteDecision decide(DrugAgentReq req, AgentContext context);

    /**
     * 兼容现有调用方的路由方法。
     *
     * @param req      请求对象
     * @param context  Agent 上下文
     * @return 路由到的场景枚举
     */
    SceneEnum route(DrugAgentReq req, AgentContext context);
}
