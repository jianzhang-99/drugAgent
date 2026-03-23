package com.liang.drugagent.agent;

import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.resp.DrugAgentResp;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 上层 Agent 统一调度入口。
 *
 * <p>所有 AI 请求统一从该入口进入，完成以下职责：
 * <ol>
 *   <li>意图理解与场景识别</li>
 *   <li>决策融合与置信度判断</li>
 *   <li>必要时向用户澄清</li>
 *   <li>调度下层 workflow 执行</li>
 * </ol>
 *
 * <p>Phase 1 阶段：先作为统一入口，后续逐步接管场景识别逻辑。
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
public interface UpperAgentOrchestrator {

    /**
     * 同步处理 AI 请求。
     *
     * <p>完整流程：
     * <ol>
     *   <li>构建 AgentContext</li>
     *   <li>执行场景识别（规则 + LLM）</li>
     *   <li>判断是否需要澄清</li>
     *   <li>分发到对应 workflow</li>
     *   <li>返回统一响应</li>
     * </ol>
     *
     * @param req 请求对象
     * @return 统一响应
     */
    DrugAgentResp handle(DrugAgentReq req);

    /**
     * 流式处理 AI 请求。
     *
     * @param req 请求对象
     * @return SSE Emitter
     */
    SseEmitter streamHandle(DrugAgentReq req);

    /**
     * 执行场景识别决策（供外部调用方查看决策过程）。
     *
     * <p>Phase 2 扩展：此方法将基于上层 Agent LLM 决策，
     * 当前 Phase 1 仍委托给 SceneRouter。
     *
     * @param req 请求对象
     * @param context Agent 上下文
     * @return 路由决策结果
     */
    WorkflowRouteDecision decide(DrugAgentReq req, AgentContext context);
}
