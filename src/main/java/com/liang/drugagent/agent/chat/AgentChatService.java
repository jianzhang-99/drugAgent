package com.liang.drugagent.agent.chat;

import com.liang.drugagent.agent.assembler.AgentResponseService;
import com.liang.drugagent.agent.scene.AgentSceneService;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.common.entity.ChatMessage;
import com.liang.drugagent.scene.common.entity.ChatSession;
import com.liang.drugagent.scene.common.service.ChatMemoryService;
import com.liang.drugagent.shared.domain.model.AgentExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
    private final ChatMemoryService chatMemoryService;


    /**
     * 同步对话处理，后续修改为 SSE 流对话。
     *
     * @param req 对话请求
     * @return 统一响应
     */
    public AgentChatResp chat(AgentChatReq req) {
        log.info("[AgentChatService] 收到对话请求: sessionId={}",
                req != null ? req.getSessionId() : null);

        try {
            // 1. 查看会话信息，提取上下文
            ChatSession session = agentSessionService.getOrCreateSession(
                    req.getSessionId()
            );
            List<ChatMessage> historyMessages = chatMemoryService.getMessagesBySessionId(session.getId());
            log.info("[AgentChatService] 读取会话历史: sessionId={}, historyCount={}",
                    session.getId(), historyMessages.size());

            // 2. 结合本次对话信息，重新构建上下文
            AgentChatContext context = AgentChatContext.from(req);
            context.setSession(session);
            context.setHistoryMessages(historyMessages);
            String recentSummary = buildConversationSummary(historyMessages);
            context.setRecentSummary(recentSummary);
            log.debug("[AgentChatService] 构建执行上下文: sessionId={}, traceId={}",
                    context.getSessionId(), context.getTraceId());

            // 3. 调用 AgentSceneService 执行场景判断与分发
            AgentSceneService.AgentSceneExecution execution = agentSceneService.decideAndExecute(context, req);

            // 4. 如需要澄清，直接返回澄清响应
            if (execution.isNeedsClarification()) {
                return buildClarificationResp(context, execution.getDecision(), execution.getClarificationQuestion());
            }

            AgentExecutionResult executionResult = execution.getExecutionResult();

            // 5. 整理本次对话的精炼上下文并持久化到会话中
            agentSessionService.saveUserMessage(context.getSessionId(), req.getQuery(), null);
            String assistantContent = executionResult.getAnswer() != null ? executionResult.getAnswer() : executionResult.getSummary();
            String messageType = executionResult.isNeedsFallback() ? "assistant_clarify" : "assistant_text";
            agentSessionService.saveAssistantMessage(context.getSessionId(), assistantContent, null, messageType);
            if (executionResult.isShouldUpdateTitle()) {
                agentSessionService.updateSessionTitleIfNeeded(context.getSessionId(), req.getQuery());
            }

            // 6. 拿到模型处理完成的结果返回，封装交给前端
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
     * 构建会话摘要。
     */
    private String buildConversationSummary(List<ChatMessage> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return null;
        }
        int maxChars = 500;
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : historyMessages) {
            if (sb.length() > maxChars) {
                break;
            }
            sb.append(msg.getContent()).append(" ");
        }
        String summary = sb.toString().trim();
        return summary.length() > maxChars ? summary.substring(0, maxChars) + "..." : summary;
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
