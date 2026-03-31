package com.liang.drugagent.agent.chat;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.facade.TenderReviewSceneService;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import com.liang.drugagent.shared.llm.LlmProviderType;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.shared.model.WorkflowRouteDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            // 1. 调用场景服务获取 workflow 结果
            AgentExecutionResult result = tenderReviewSceneService.execute(context, req);

            // 2. 生成标题
            result.setGeneratedTitle(generateTenderReviewTitle(result));
            result.setShouldUpdateTitle(true);

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
     * 生成标书审查标题。
     */
    private String generateTenderReviewTitle(AgentExecutionResult result) {
        String riskLevel = result.getRiskLevel() != null ? result.getRiskLevel() : "未知";
        return "标书审查-" + riskLevel;
    }

    /**
     * 场景路由决策。
     *
     * <p>每次对话都重新判断场景，不依赖历史锁定。
     * 判断优先级（从高到低）：
     * 1. 有新上传文件 -> 标书审查（本次上传的文件才有意义）
     * 2. query 关键词匹配 -> 可覆盖 sceneHint
     * 3. LLM 意图分类 -> 可覆盖 sceneHint，最灵活
     * 4. sceneHint 作为默认值
     * 5. 降级到通用对话
     */
    private WorkflowRouteDecision decideRoute(AgentChatContext context, AgentChatReq req) {
        String query = context.getQuery();

        // 1. 有新上传文件时，直接路由到标书审查
        if (hasUploadedFiles(req)) {
            log.info("[AgentSceneService] 检测到上传文件，自动识别为标书审查场景");
            return WorkflowRouteDecision.builder()
                    .scene(SceneEnum.TENDER_REVIEW)
                    .source("auto-detect")
                    .reason("检测到上传文件，自动路由到标书审查")
                    .confidence(0.95)
                    .requiresClarification(false)
                    .build();
        }

        // 2. 检查 query 关键词（可覆盖 sceneHint）
        if (query != null) {
            SceneEnum keywordScene = detectSceneByKeywords(query);
            if (keywordScene != null) {
                log.info("[AgentSceneService] 通过关键词识别场景: {}", keywordScene);
                return WorkflowRouteDecision.builder()
                        .scene(keywordScene)
                        .source("rule")
                        .reason("关键词匹配: " + keywordScene.name())
                        .confidence(0.85)
                        .requiresClarification(false)
                        .build();
            }
        }

        // 3. LLM 意图分类（可覆盖 sceneHint，最灵活的判断方式）
        SceneEnum llmScene = classifyIntent(query, context.getModel());
        if (llmScene != SceneEnum.DEFAULT) {
            log.info("[AgentSceneService] LLM 意图分类识别场景: {}", llmScene);
            return WorkflowRouteDecision.builder()
                    .scene(llmScene)
                    .source("llm-classify")
                    .reason("LLM 意图分类: " + llmScene.name())
                    .confidence(0.8)
                    .requiresClarification(false)
                    .build();
        }

        // 4. sceneHint 作为默认值（兜底）
        SceneEnum hintScene = resolveSceneHint(req);
        if (hintScene != null) {
            log.info("[AgentSceneService] 使用 sceneHint 默认场景: {}", hintScene);
            return WorkflowRouteDecision.builder()
                    .scene(hintScene)
                    .source("sceneHint")
                    .reason("使用 sceneHint 默认场景: " + hintScene)
                    .confidence(0.6)
                    .requiresClarification(false)
                    .build();
        }

        // 5. 最终降级到通用对话
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
     * 检查请求中是否包含上传文件。
     */
    private boolean hasUploadedFiles(AgentChatReq req) {
        // 检查 MultipartFile[] 是否有文件
        if (req.getFiles() != null && req.getFiles().length > 0) {
            return true;
        }
        // 检查 fileIds 列表是否有文件ID
        if (req.getFileIds() != null && !req.getFileIds().isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * 分发到通用对话。
     */
    private AgentSceneExecution dispatchToGeneralChat(AgentChatContext context, String query,
                                                       WorkflowRouteDecision decision) {
        log.info("[AgentSceneService] 分发到通用对话");

        try {
            GeneralChatResult chatResult = generalChatWithTitle(query, context.getSessionId(), context.getModel());

            return AgentSceneExecution.builder()
                    .decision(decision)
                    .executionResult(AgentExecutionResult.builder()
                            .success(true)
                            .answer(chatResult.answer)
                            .summary("通用对话")
                            .generatedTitle(chatResult.title)
                            .shouldUpdateTitle(true)
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
     * 通用对话结果（含回答和标题）。
     */
    private record GeneralChatResult(String answer, String title) {}

    /**
     * 通用对话处理（同时生成标题）。
     */
    private GeneralChatResult generalChatWithTitle(String query, String sessionId, String model) {
        String systemPrompt = """
                你是一个专业的医疗监管AI助手，负责回答关于药品监管、医疗器械监管、标书审查、合同审核等相关问题。

                请用专业、清晰的语言回答用户的问题。如果不确定答案，请如实告知用户。

                回答完成后，请在最后一行输出会话标题，格式为：【会话标题】xxx
                会话标题应该简洁明了，不超过20个字，能够概括用户询问的核心内容。
                """;

        try {
            LlmProviderType provider = LlmProviderType.fromConfigKey(model);
            LlmRequest request = LlmRequest.builder()
                    .provider(provider)
                    .model(model)
                    .sessionId(sessionId)
                    .systemPrompt(systemPrompt)
                    .messages(List.of(LlmRequest.ChatMessage.builder()
                            .role("user")
                            .content(query)
                            .build()))
                    .build();
            String fullResponse = llmService.chat(request).getContent();

            // 从回答中提取标题（最后一行格式：【会话标题】xxx）
            String title = "新对话";
            String answer = fullResponse;

            int titleIndex = fullResponse.lastIndexOf("【会话标题】");
            if (titleIndex != -1) {
                title = fullResponse.substring(titleIndex + 7).trim();
                answer = fullResponse.substring(0, titleIndex).trim();
            }

            // 限制标题长度
            if (title.length() > 20) {
                title = title.substring(0, 20);
            }

            return new GeneralChatResult(answer, title);
        } catch (Exception e) {
            throw new RuntimeException("通用对话失败: " + e.getMessage(), e);
        }
    }

    // ==================== LLM 意图分类 ====================

    private static final String INTENT_CLASSIFY_PROMPT = """
            你是一个专业的医疗监管领域意图分类器。
            根据用户输入，判断用户想要什么类型的AI服务。

            场景定义：
            - TENDER_REVIEW（标书审查）：用户想要审查标书、检测围标串标、分析标书相似度
            - CONTRACT_PRECHECK（合同预审）：用户想要审核合同、检查合同风险
            - RISK_ALERT（风险预警）：用户想要了解药品/医疗器械合规风险、监管预警
            - DEFAULT（通用对话）：用户询问药品监管政策、医疗行业知识、通用问题

            输出格式要求：
            只输出一个英文单词作为分类结果，不要任何其他内容。
            - 如果是标书审查，返回：TENDER_REVIEW
            - 如果是合同预审，返回：CONTRACT_PRECHECK
            - 如果是风险预警，返回：RISK_ALERT
            - 如果是通用问题，返回：DEFAULT
            """;

    /**
     * 通过 LLM 分类用户意图。
     */
    private SceneEnum classifyIntent(String userQuery, String model) {
        if (userQuery == null || userQuery.isBlank()) {
            return SceneEnum.DEFAULT;
        }

        try {
            LlmProviderType provider = LlmProviderType.fromConfigKey(model);
            LlmRequest request = LlmRequest.builder()
                    .provider(provider)
                    .model(model)
                    .sessionId("intent-classify")
                    .systemPrompt(INTENT_CLASSIFY_PROMPT)
                    .messages(List.of(LlmRequest.ChatMessage.builder()
                            .role("user")
                            .content(userQuery)
                            .build()))
                    .build();
            String response = llmService.chatForRouting(request).getContent();
            return parseSceneFromLLMResponse(response);
        } catch (Exception e) {
            log.warn("[AgentSceneService] 意图分类失败: {}, 降级为 DEFAULT", e.getMessage());
            return SceneEnum.DEFAULT;
        }
    }

    private SceneEnum parseSceneFromLLMResponse(String response) {
        if (response == null || response.isBlank()) {
            return SceneEnum.DEFAULT;
        }

        String trimmed = response.trim().toUpperCase();

        for (SceneEnum scene : SceneEnum.values()) {
            if (trimmed.contains(scene.name())) {
                log.info("[AgentSceneService] LLM 意图分类结果: {}, 原始响应: {}", scene, response);
                return scene;
            }
        }

        log.warn("[AgentSceneService] 无法解析 LLM 分类结果: {}, 降级为 DEFAULT", response);
        return SceneEnum.DEFAULT;
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
