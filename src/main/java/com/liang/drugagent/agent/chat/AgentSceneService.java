package com.liang.drugagent.agent.chat;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.facade.TenderReviewSceneService;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.shared.model.WorkflowRouteDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 场景服务。
 *
 * <p>负责接住对话请求并分发到对应场景执行器。
 * 当前版本支持：
 * <ul>
 *   <li>通用对话（DEFAULT）</li>
 *   <li>标书审查（TENDER_REVIEW）</li>
 * </ul>
 *
 * <p>场景路由判断规则（按优先级）：
 * <ol>
 *   <li>sceneHint 显式指定场景</li>
 *   <li>metadata.sceneHint 显式指定场景</li>
 *   <li>query 关键词匹配（围标/串标/标书雷同/标书审查）</li>
 * </ol>
 *
 * @author liangjiajian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSceneService {

    private final LlmService llmService;
    private final TenderReviewSceneService tenderReviewSceneService;

    /**
     * 执行对话并返回结果。
     *
     * <p>完整流程：
     * <ol>
     *   <li>判断场景类型（TENDER_REVIEW 或 DEFAULT）</li>
     *   <li>路由到对应场景执行器</li>
     *   <li>返回统一执行结果</li>
     * </ol>
     *
     * @param context 执行上下文
     * @param req     对话请求
     * @return 场景执行结果
     */
    public AgentSceneExecution decideAndExecute(AgentChatContext context, AgentChatReq req) {
        log.info("[AgentSceneService] 开始处理对话请求: sessionId={}", context.getSessionId());

        try {
            // 1. 路由决策
            WorkflowRouteDecision decision = decideRoute(context, req);
            context.setSceneType(decision.getScene());

            // 2. 根据场景分发
            if (decision.getScene() == SceneEnum.TENDER_REVIEW) {
                return executeTenderReview(context, req, decision);
            } else {
                return dispatchToGeneralChat(context, req.getQuery(), decision);
            }

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
     * 执行标书审查场景。
     */
    private AgentSceneExecution executeTenderReview(AgentChatContext context, AgentChatReq req,
                                                     WorkflowRouteDecision decision) {
        log.info("[AgentSceneService] 路由到标书审查场景, source={}, reason={}",
                decision.getSource(), decision.getReason());

        try {
            AgentExecutionResult result = tenderReviewSceneService.execute(context, req);
            return AgentSceneExecution.builder()
                    .decision(decision)
                    .executionResult(result)
                    .needsClarification(false)
                    .build();
        } catch (Exception e) {
            log.error("[AgentSceneService] 标书审查执行失败: {}", e.getMessage(), e);
            return AgentSceneExecution.builder()
                    .decision(decision)
                    .executionResult(AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW,
                            "标书审查执行失败: " + e.getMessage()))
                    .needsClarification(false)
                    .build();
        }
    }

    /**
     * 场景路由决策。
     *
     * @return 路由决策结果
     */
    private WorkflowRouteDecision decideRoute(AgentChatContext context, AgentChatReq req) {
        // 1. 优先检查 sceneHint（前端或调用方显式指定）
        SceneEnum hintScene = resolveSceneHint(req);
        if (hintScene != null) {
            log.info("[AgentSceneService] 通过 sceneHint 识别场景: {}", hintScene);
            return WorkflowRouteDecision.builder()
                    .scene(hintScene)
                    .source("sceneHint")
                    .reason("前端显式指定场景: " + hintScene)
                    .confidence(1.0)
                    .requiresClarification(false)
                    .build();
        }

        // 2. 检查 query 关键词
        String query = context.getQuery();
        if (query != null) {
            SceneEnum keywordScene = detectSceneByKeywords(query);
            if (keywordScene != null) {
                log.info("[AgentSceneService] 通过关键词识别场景: {}", keywordScene);
                return WorkflowRouteDecision.builder()
                        .scene(keywordScene)
                        .source("rule")
                        .reason("关键词匹配: " + keywordScene.name())
                        .confidence(0.9)
                        .requiresClarification(false)
                        .build();
            }
        }

        // 3. 默认走通用对话
        log.info("[AgentSceneService] 未识别特定场景，走通用对话");
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.DEFAULT)
                .source("fallback")
                .reason("未匹配特定场景，使用默认对话")
                .confidence(0.5)
                .requiresClarification(false)
                .build();
    }

    /**
     * 从请求中解析 sceneHint。
     */
    private SceneEnum resolveSceneHint(AgentChatReq req) {
        // 优先从 req.sceneHint 获取
        if (req.getSceneHint() != null && !req.getSceneHint().isBlank()) {
            SceneEnum scene = SceneEnum.fromHint(req.getSceneHint());
            if (scene != null) {
                return scene;
            }
        }

        // 尝试从 metadata.sceneHint 获取
        if (req.getMetadata() != null && req.getMetadata().containsKey("sceneHint")) {
            Object hintValue = req.getMetadata().get("sceneHint");
            if (hintValue != null) {
                SceneEnum scene = SceneEnum.fromHint(hintValue.toString());
                if (scene != null) {
                    return scene;
                }
            }
        }

        return null;
    }

    /**
     * 通过关键词检测场景类型。
     */
    private SceneEnum detectSceneByKeywords(String query) {
        String lowerQuery = query.toLowerCase();

        // 标书审查关键词
        if (lowerQuery.contains("围标") || lowerQuery.contains("串标")
                || lowerQuery.contains("标书雷同") || lowerQuery.contains("标书审查")
                || (lowerQuery.contains("标书") && (lowerQuery.contains("风险") || lowerQuery.contains("比对") || lowerQuery.contains("比较")))
                || lowerQuery.contains("投标文件") || lowerQuery.contains("竞标")) {
            return SceneEnum.TENDER_REVIEW;
        }

        // 其他场景可在此扩展
        return null;
    }

    /**
     * 分发到通用对话。
     */
    private AgentSceneExecution dispatchToGeneralChat(AgentChatContext context, String query,
                                                       WorkflowRouteDecision decision) {
        log.info("[AgentSceneService] 分发到通用对话");

        try {
            String answer = generalChat(query, context.getSessionId());

            return AgentSceneExecution.builder()
                    .decision(decision)
                    .executionResult(AgentExecutionResult.builder()
                            .success(true)
                            .answer(answer)
                            .summary("通用对话")
                            .steps(List.of("问题理解", "回复生成"))
                            .needsFallback(false)
                            .build())
                    .needsClarification(false)
                    .build();

        } catch (Exception e) {
            log.error("[AgentSceneService] 通用对话执行失败: {}", e.getMessage(), e);
            return AgentSceneExecution.builder()
                    .decision(decision)
                    .executionResult(AgentExecutionResult.builder()
                            .success(false)
                            .answer("处理失败，请稍后重试。")
                            .errorMessage(e.getMessage())
                            .needsFallback(true)
                            .build())
                    .needsClarification(false)
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
         * 路由决策。
         */
        private WorkflowRouteDecision decision;

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
