package com.liang.drugagent.agent.assembler;

import com.liang.drugagent.agent.chat.AgentChatContext;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.shared.domain.model.AgentExecutionResult;
import com.liang.drugagent.shared.domain.model.WorkflowRouteDecision;

/**
 * Agent 响应服务接口。
 *
 * <p>负责将下游执行结果统一转换为前端可消费的 AgentChatResp 结构。
 * 确保不同场景、不同执行器返回给前端的数据格式一致。</p>
 *
 * <p>主要职责：
 * <ul>
 *   <li>将 AgentExecutionResult 转换为 AgentChatResp</li>
 *   <li>填充 traceId、scene、routeReason、confidence 等元信息</li>
 *   <li>整理 summary、answer、report、evidence 等业务数据</li>
 *   <li>为失败或降级结果提供统一响应结构</li>
 * </ul>
 *
 * @author liangjiajian
 * @see AgentExecutionResult
 * @see WorkflowRouteDecision
 * @see AgentChatResp
 */
public interface AgentResponseService {

    /**
     * 构建统一响应。
     *
     * <p>将执行结果根据路由决策装配成前端可消费的响应格式。</p>
     *
     * @param context         Agent 执行上下文
     * @param decision        路由决策
     * @param executionResult 执行结果
     * @return 统一响应对象
     */
    AgentChatResp buildResponse(AgentChatContext context, WorkflowRouteDecision decision, AgentExecutionResult executionResult);
}
