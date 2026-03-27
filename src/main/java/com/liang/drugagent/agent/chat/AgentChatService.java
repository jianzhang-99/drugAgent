package com.liang.drugagent.agent.chat;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.common.entity.ChatSession;
import com.liang.drugagent.shared.domain.model.AgentExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 主服务 - 上层会话编排服务。
 *
 * <p>核心职责：
 * <ul>
 *   <li>面向前端接住一次对话请求</li>
 *   <li>把请求整理成统一上下文</li>
 *   <li>驱动路由、场景分发、降级和结果回写</li>
 *   <li>将后端复杂执行链路包装成前端可消费的统一响应</li>
 * </ul>
 *
 * <p>标准主流程：会话中提取前文信息 -> 构建上下文 -> AgentSceneService.decideAndExecute -> 结果回写 -> 响应返回
 *
 * @author liangjiajian
 * @see AgentSceneService
 * @see AgentResponseService
 * @see AgentSessionService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final AgentSceneService agentSceneService;
    private final AgentSessionService agentSessionService;
    private final AgentResponseService agentResponseService;


    /**
     * 同步对话处理主入口。
     *
     * <p>完整流程：
     * 1. 通过 AgentSessionService 加载会话上下文
     * 2. 构建本轮执行上下文
     * 3. 调用 AgentSceneService 执行场景判断与分发
     * 4. 保存用户消息和助手消息
     * 5. 返回统一响应
     *
     * @param req 对话请求
     * @return 统一响应
     */
    public AgentChatResp chat(AgentChatReq req) {
        log.info("[AgentChatService] 收到对话请求: sessionId={}",
                req != null ? req.getSessionId() : null);

        try {
            // 1. 获取或创建会话，加载上下文
            AgentSessionService.AgentSessionContext sessionContext = agentSessionService.loadSessionContext(req.getSessionId());
            ChatSession session = sessionContext.session();
            if (session == null) {
                session = agentSessionService.getOrCreateSession(req.getSessionId());
            }

            // 2. 构建本轮执行上下文
            AgentChatContext context = AgentChatContext.from(req);
            context.setSession(session);
            context.setHistoryMessages(sessionContext.recentMessages());
            context.setRecentSummary(sessionContext.summary());
            log.debug("[AgentChatService] 构建执行上下文: sessionId={}, traceId={}, historyCount={}",
                    context.getSessionId(), context.getTraceId(),
                    sessionContext.recentMessages() != null ? sessionContext.recentMessages().size() : 0);

            // 3. 调用 AgentSceneService 执行场景判断与分发
            AgentSceneService.AgentSceneExecution execution = agentSceneService.decideAndExecute(context, req);

            // 4. 如需要澄清，直接返回澄清响应
            if (execution.isNeedsClarification()) {
                return buildClarificationResp(context, execution.getDecision(), execution.getClarificationQuestion());
            }

            AgentExecutionResult executionResult = execution.getExecutionResult();

            // 5. 保存用户消息
            agentSessionService.saveUserMessage(context.getSessionId(), req.getQuery(), null);

            // 6. 保存助手消息
            String assistantContent = executionResult.getAnswer() != null ? executionResult.getAnswer() : executionResult.getSummary();
            String messageType = executionResult.isNeedsFallback() ? "assistant_clarify" : "assistant_text";
            agentSessionService.saveAssistantMessage(context.getSessionId(), assistantContent, null, messageType);

            // 7. 如需要更新标题
            if (executionResult.isShouldUpdateTitle()) {
                agentSessionService.updateSessionTitleIfNeeded(context.getSessionId(), req.getQuery());
            }

            // 8. 更新会话摘要
            if (executionResult.getSummary() != null) {
                agentSessionService.updateSessionSummary(context.getSessionId(), executionResult.getSummary());
            }

            // 9. 返回统一响应
            return agentResponseService.buildResponse(context, execution.getDecision(), executionResult);
        } catch (Exception e) {
            log.error("[AgentChatService] 对话执行失败: {}", e.getMessage(), e);
            return fallback(req, null, e);
        }
    }

    /**
     * 构建澄清响应。
     */
    private AgentChatResp buildClarificationResp(AgentChatContext context,
                                                 com.liang.drugagent.shared.domain.model.WorkflowRouteDecision decision,
                                                 String clarifyQuestion) {
        log.info("[AgentChatService] 需要澄清: sessionId={}, question={}",
                context.getSessionId(), clarifyQuestion);

        AgentExecutionResult clarifyResult = AgentExecutionResult.builder()
                .success(false)
                .scene(decision != null ? decision.getScene() : SceneEnum.UNKNOWN)
                .errorMessage(clarifyQuestion)
                .needsFallback(true)
                .build();

        return agentResponseService.buildResponse(context, decision, clarifyResult);
    }

    /**
     * 降级处理。
     */
    private AgentChatResp fallback(AgentChatReq req, com.liang.drugagent.shared.domain.model.WorkflowRouteDecision decision, Exception e) {
        log.warn("[AgentChatService] 执行降级处理: {}", e.getMessage());

        AgentChatResp resp = new AgentChatResp();
        resp.setSessionId(req != null ? req.getSessionId() : null);
        resp.setScene(SceneEnum.UNKNOWN.name());
        resp.setAnswer("系统处理遇到问题，请稍后重试。");
        resp.setSummary("系统异常");
        resp.setRiskLevel("UNKNOWN");
        resp.setScore(0);
        resp.setRequiresClarification(true);
        resp.setClarificationQuestion("系统处理遇到问题，请稍后重试或联系管理员。");

        return resp;
    }

}
