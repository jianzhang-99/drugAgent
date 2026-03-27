package com.liang.drugagent.agent.chat;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.shared.domain.model.AgentExecutionResult;
import com.liang.drugagent.shared.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 场景服务。
 *
 * <p>当前版本：场景已去除，会话统一走通用对话链路。
 * 该服务负责接住对话请求并分发到通用对话执行器。</p>
 *
 * @author liangjiajian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSceneService {

    private final LlmService llmService;

    /**
     * 执行对话并返回结果。
     *
     * @param context 执行上下文
     * @param req     对话请求
     * @return 场景执行结果
     */
    public AgentSceneExecution decideAndExecute(AgentChatContext context, AgentChatReq req) {
        log.info("[AgentSceneService] 开始处理对话请求: sessionId={}", context.getSessionId());

        try {
            AgentExecutionResult result = dispatchToGeneralChat(context, req.getQuery());

            return AgentSceneExecution.builder()
                    .decision(null)
                    .executionResult(result)
                    .needsClarification(false)
                    .build();

        } catch (Exception e) {
            log.error("[AgentSceneService] 对话执行失败: {}", e.getMessage(), e);
            AgentExecutionResult errorResult = AgentExecutionResult.builder()
                    .success(false)
                    .answer("系统处理遇到问题，请稍后重试。")
                    .errorMessage(e.getMessage())
                    .needsFallback(true)
                    .build();

            return AgentSceneExecution.builder()
                    .decision(null)
                    .executionResult(errorResult)
                    .needsClarification(true)
                    .clarificationQuestion("系统处理遇到问题，请稍后重试或联系管理员。")
                    .build();
        }
    }

    /**
     * 分发到通用对话。
     */
    private AgentExecutionResult dispatchToGeneralChat(AgentChatContext context, String query) {
        log.info("[AgentSceneService] 分发到通用对话");

        try {
            String answer = generalChat(query, context.getSessionId());

            return AgentExecutionResult.builder()
                    .success(true)
                    .answer(answer)
                    .summary("通用对话")
                    .steps(List.of("问题理解", "回复生成"))
                    .needsFallback(false)
                    .build();

        } catch (Exception e) {
            log.error("[AgentSceneService] 通用对话执行失败: {}", e.getMessage(), e);
            return AgentExecutionResult.builder()
                    .success(false)
                    .answer("处理失败，请稍后重试。")
                    .errorMessage(e.getMessage())
                    .needsFallback(true)
                    .build();
        }
    }

    /**
     * 通用对话处理。
     */
    private String generalChat(String query, String sessionId) {
        String systemPrompt = """
                你是一个专业的医疗监管AI助手，负责回答关于药品监管、医疗器械监管、标书审查、合同审核等相关问题。

                请用专业、清晰的语言回答用户的问题。如果不确定答案，请如实告知用户。
                """;

        try {
            return llmService.chat(query, systemPrompt, "general-chat");
        } catch (Exception e) {
            throw new RuntimeException("通用对话失败: " + e.getMessage(), e);
        }
    }

    // ==================== 内部类 ====================

    /**
     * 场景执行结果。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AgentSceneExecution {

        /**
         * 路由决策（当前版本为 null）。
         */
        private com.liang.drugagent.shared.domain.model.WorkflowRouteDecision decision;

        /**
         * 执行结果。
         */
        private AgentExecutionResult executionResult;

        /**
         * 是否需要澄清。
         */
        private boolean needsClarification;

        /**
         * 澄清问题（当 needsClarification 为 true 时）。
         */
        private String clarificationQuestion;
    }
}
