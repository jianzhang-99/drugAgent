package com.liang.drugagent.agent.chat;

import com.liang.drugagent.agent.prompt.shared.base.SharedBasePrompt;
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
import com.liang.drugagent.shared.llm.LlmResponse;
import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.RagOutcome;
import com.liang.drugagent.shared.tool.KnowledgeRetrievalTool;
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
    private final KnowledgeRetrievalTool knowledgeRetrievalTool;

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

            // 1.5 如果需要澄清，直接返回澄清响应
            if (decision.isRequiresClarification()) {
                log.info("[AgentSceneService] 路由判定需要澄清，scene={}, reason={}",
                        decision.getScene(), decision.getReason());
                return AgentSceneExecution.builder()
                        .decision(decision)
                        .executionResult(AgentExecutionResult.builder()
                                .success(false)
                                .scene(decision.getScene())
                                .clarificationQuestion(decision.getClarificationQuestion())
                                .needsFallback(true)
                                .build())
                        .needsClarification(true)
                        .clarificationQuestion(decision.getClarificationQuestion())
                        .build();
            }

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
     * 场景路由决策 - 分层判断 + 按需升级。
     *
     * <p>设计原则：
     * <ul>
     *   <li>强命中：直接进场景，不跑 LLM</li>
     *   <li>弱命中：先澄清，不跑 LLM</li>
     *   <li>未命中：普通对话，也不跑 LLM</li>
     * </ul>
     *
     * <p>判断优先级（从高到低）：
     * <ol>
     *   <li>显式信号：有上传文件 或 sceneHint 指定 -> 强命中，直接定场景</li>
     *   <li>规则强匹配：关键词明确指向业务场景 -> 强命中，进场景</li>
     *   <li>规则弱匹配：像业务请求但不确定 -> 弱命中，先澄清</li>
     *   <li>未命中：普通闲聊/知识问答 -> DEFAULT 场景直接回答，不跑 LLM</li>
     * </ol>
     *
     * <p>只有当【显式信号 + 规则匹配】都无法判断，且请求看起来像业务请求时，
     * 才使用 LLM 做兜底判断。但这种情况应该很少。
     */
    private WorkflowRouteDecision decideRoute(AgentChatContext context, AgentChatReq req) {
        String query = context.getQuery();

        // ========== 第一层：显式信号（最强，无需 LLM）==========

        // 1.1 有新上传文件 -> 强命中进标书审查
        if (hasUploadedFiles(req)) {
            log.info("[AgentSceneService] 【显式信号】检测到上传文件，直接进标书审查");
            return WorkflowRouteDecision.builder()
                    .scene(SceneEnum.TENDER_REVIEW)
                    .source("explicit-file")
                    .reason("检测到上传文件，强制路由到标书审查")
                    .confidence(1.0)
                    .requiresClarification(false)
                    .build();
        }

        // 1.2 sceneHint 明确指定场景 -> 强命中
        SceneEnum hintScene = resolveSceneHint(req);
        if (hintScene != null && hintScene != SceneEnum.DEFAULT && hintScene != SceneEnum.UNKNOWN) {
            log.info("[AgentSceneService] 【显式信号】sceneHint 指定场景: {}", hintScene);
            return WorkflowRouteDecision.builder()
                    .scene(hintScene)
                    .source("explicit-hint")
                    .reason("sceneHint 显式指定: " + hintScene.name())
                    .confidence(0.95)
                    .requiresClarification(false)
                    .build();
        }

        // ========== 第二层：规则匹配（成本低、可控、快）==========

        if (query != null && !query.isBlank()) {
            // 2.1 强规则匹配 -> 强命中，直接进场景
            RuleMatchResult strongMatch = matchStrongRules(query);
            if (strongMatch != null) {
                log.info("[AgentSceneService] 【强规则匹配】{} -> {}", strongMatch.scene, strongMatch.reason);
                return WorkflowRouteDecision.builder()
                        .scene(strongMatch.scene)
                        .source("rule-strong")
                        .reason(strongMatch.reason)
                        .confidence(0.85)
                        .requiresClarification(false)
                        .build();
            }

            // 2.2 弱规则匹配 -> 弱命中，先澄清
            RuleMatchResult weakMatch = matchWeakRules(query);
            if (weakMatch != null) {
                log.info("[AgentSceneService] 【弱规则匹配】需要澄清 - {}", weakMatch.reason);
                return WorkflowRouteDecision.builder()
                        .scene(weakMatch.scene)
                        .source("rule-weak")
                        .reason(weakMatch.reason)
                        .confidence(0.5)
                        .requiresClarification(true)
                        .clarificationQuestion(weakMatch.clarificationQuestion)
                        .build();
            }
        }

        // ========== 第三层：未命中 -> 普通对话（不跑 LLM）==========

        // 没有强/弱规则命中，说明是普通对话，直接走 DEFAULT 场景
        // 注意：这里不调用 LLM 意图分类，直接判定为普通对话
        log.info("[AgentSceneService] 【未命中】普通对话，直接走 DEFAULT 场景");
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.DEFAULT)
                .source("default")
                .reason("无业务场景信号，使用默认对话")
                .confidence(0.5)
                .requiresClarification(false)
                .build();
    }

    /**
     * 强规则匹配：关键词明确指向某个业务场景。
     * 匹配到则直接进场景，不需澄清。
     */
    private RuleMatchResult matchStrongRules(String query) {
        String lowerQuery = query.toLowerCase();

        // ===== 标书审查强规则 =====
        // 包含"审查/比对" + "标书/围标/串标" -> 强命中
        if (containsAny(lowerQuery, "围标", "串标", "标书雷同", "竞标", "投标文件")
                || (containsAny(lowerQuery, "标书") && containsAny(lowerQuery, "审查", "比对", "比较", "风险"))) {
            return new RuleMatchResult(SceneEnum.TENDER_REVIEW, "标书审查强规则命中");
        }

        // 用户明确要求"帮我审"、"帮我查这两份" -> 强命中
        if ((lowerQuery.contains("帮我") || lowerQuery.contains("请帮我"))
                && (lowerQuery.contains("审") || lowerQuery.contains("查"))
                && (lowerQuery.contains("标书") || lowerQuery.contains("围标"))) {
            return new RuleMatchResult(SceneEnum.TENDER_REVIEW, "用户明确要求审查标书");
        }

        // ===== 合同预审强规则 =====
        if (containsAny(lowerQuery, "合同预审", "合同审查", "合同风险", "合同条款")
                || (containsAny(lowerQuery, "合同") && containsAny(lowerQuery, "审查", "风险", "合规"))) {
            return new RuleMatchResult(SceneEnum.CONTRACT_PRECHECK, "合同审查强规则命中");
        }

        // ===== 风险预警强规则 =====
        if (containsAny(lowerQuery, "风险预警", "风险提示", "合规风险", "违规预警")
                || (containsAny(lowerQuery, "药品") && containsAny(lowerQuery, "风险", "预警", "警告"))) {
            return new RuleMatchResult(SceneEnum.RISK_ALERT, "风险预警强规则命中");
        }

        return null;
    }

    /**
     * 弱规则匹配：像业务请求但不够确定，需要澄清。
     */
    private RuleMatchResult matchWeakRules(String query) {
        String lowerQuery = query.toLowerCase();

        // 提到"标书"但没有明确审查意图
        if (containsAny(lowerQuery, "标书")) {
            return new RuleMatchResult(
                    SceneEnum.TENDER_REVIEW,
                    "提到标书但无明确审查指令",
                    "您是想审查标书内容，还是其他关于标书的问题？比如：帮我看看这份标书有没有围标风险，或者标书怎么写更规范？"
            );
        }

        // 提到"合同"但没有明确审查意图
        if (containsAny(lowerQuery, "合同")) {
            return new RuleMatchResult(
                    SceneEnum.CONTRACT_PRECHECK,
                    "提到合同但无明确审查指令",
                    "您是想预审合同条款，还是其他关于合同的问题？比如：帮我看看这份合同有没有风险，或者合同应该怎么写？"
            );
        }

        // 提到"文件"、"文档"且上下文像业务
        if (containsAny(lowerQuery, "文件", "文档")) {
            return new RuleMatchResult(
                    SceneEnum.DEFAULT,
                    "提到文件但无明确业务场景",
                    "请问您是想审查标书、预审合同，还是其他类型的文件处理？"
            );
        }

        return null;
    }

    /**
     * 规则匹配结果。
     */
    private record RuleMatchResult(SceneEnum scene, String reason, String clarificationQuestion) {
        public RuleMatchResult(SceneEnum scene, String reason) {
            this(scene, reason, null);
        }
    }

    /**
     * 字符串包含任意关键词。
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
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
            // 1. 知识检索增强
            String orgId = extractOrgId(context);
            RagOutcome ragOutcome = knowledgeRetrievalTool.retrieve(query, orgId, null);

            // 2. 判断检索结果，决定是否使用 RAG 上下文
            boolean useRagContext = "ANSWERED".equals(ragOutcome.getDecision())
                    && ragOutcome.getEvidenceList() != null
                    && !ragOutcome.getEvidenceList().isEmpty();

            if (useRagContext) {
                log.info("[AgentSceneService] 知识检索命中，使用 RAG 增强回答，evidenceCount={}",
                        ragOutcome.getEvidenceList().size());
            } else {
                log.info("[AgentSceneService] 知识检索未命中，使用纯 LLM 回答，decision={}",
                        ragOutcome.getDecision());
            }

            // 3. 构建增强后的 system prompt
            String systemPrompt = buildEnhancedSystemPrompt(ragOutcome, useRagContext);

            // 4. 调用 LLM 获取回答
            GeneralChatResult chatResult = generalChatWithTitle(query, context.getSessionId(),
                    context.getModel(), systemPrompt);

            return AgentSceneExecution.builder()
                    .decision(decision)
                    .executionResult(AgentExecutionResult.builder()
                            .success(true)
                            .answer(chatResult.answer)
                            .summary(useRagContext ? "通用对话-RAG增强" : "通用对话")
                            .generatedTitle(chatResult.title)
                            .shouldUpdateTitle(true)
                            .steps(useRagContext
                                    ? List.of("知识检索", "RAG上下文构建", "LLM回答生成")
                                    : List.of("问题理解", "回复生成"))
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
        return generalChatWithTitle(query, sessionId, model, SharedBasePrompt.GENERAL_CHAT);
    }

    /**
     * 通用对话处理（同时生成标题，支持自定义 system prompt）。
     */
    private GeneralChatResult generalChatWithTitle(String query, String sessionId, String model,
                                                    String systemPrompt) {
        try {
            LlmProviderType provider = LlmProviderType.fromConfigKey(model);
            // 根据 provider 解析正确的模型名（前端传的是 provider 标识，不是模型名）
            String effectiveModel = resolveEffectiveModel(model, provider);

            LlmRequest request = LlmRequest.builder()
                    .provider(provider)
                    .model(effectiveModel)
                    .sessionId(sessionId)
                    .systemPrompt(systemPrompt)
                    .messages(List.of(LlmRequest.ChatMessage.builder()
                            .role("user")
                            .content(query)
                            .build()))
                    .build();
            LlmResponse llmResponse = llmService.chat(request);
            if (!Boolean.TRUE.equals(llmResponse.getSuccess()) || llmResponse.getContent() == null) {
                log.error("[AgentSceneService] 通用对话失败: provider={}, success={}, errorMessage={}",
                        provider, llmResponse.getSuccess(), llmResponse.getErrorMessage());
                throw new RuntimeException("LLM调用失败: " + llmResponse.getErrorMessage());
            }
            String fullResponse = llmResponse.getContent();

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

    /**
     * 从上下文元数据中提取 orgId。
     */
    private String extractOrgId(AgentChatContext context) {
        if (context.getMetadata() != null && context.getMetadata().containsKey("orgId")) {
            Object orgId = context.getMetadata().get("orgId");
            if (orgId != null) {
                return orgId.toString();
            }
        }
        // 降级：从 sessionId 尝试提取（如果 sessionId 包含 orgId 信息）
        return null;
    }

    /**
     * 构建增强后的 system prompt。
     *
     * <p>当 RAG 检索命中时，将检索到的证据拼入 prompt 作为上下文，
     * 让 LLM 基于证据回答而不是自由发挥。</p>
     */
    private String buildEnhancedSystemPrompt(RagOutcome ragOutcome, boolean useRagContext) {
        if (!useRagContext) {
            return SharedBasePrompt.GENERAL_CHAT;
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(SharedBasePrompt.GENERAL_CHAT);
        promptBuilder.append("\n\n");
        promptBuilder.append("【参考知识】\n");
        promptBuilder.append("根据知识库检索，以下信息可作为回答参考：\n\n");

        for (int i = 0; i < ragOutcome.getEvidenceList().size(); i++) {
            EvidenceItem evidence = ragOutcome.getEvidenceList().get(i);
            promptBuilder.append("【证据").append(i + 1).append("】\n");
            if (evidence.getTitle() != null && !evidence.getTitle().isBlank()) {
                promptBuilder.append("标题：").append(evidence.getTitle()).append("\n");
            }
            if (evidence.getContent() != null && !evidence.getContent().isBlank()) {
                promptBuilder.append("内容：").append(evidence.getContent()).append("\n");
            }
            if (evidence.getSource() != null && !evidence.getSource().isBlank()) {
                promptBuilder.append("来源：").append(evidence.getSource()).append("\n");
            }
            promptBuilder.append("\n");
        }

        promptBuilder.append("请基于上述参考知识回答用户问题。如果参考知识不足以回答，请明确说明。\n");
        promptBuilder.append("回答时如引用了参考知识，可适当标注来源。");

        return promptBuilder.toString();
    }

    /**
     * 根据模型标识和 provider 解析最终使用的模型名称。
     */
    private String resolveEffectiveModel(String model, LlmProviderType provider) {
        // 如果模型名是有效的 provider 标识（minimax/dashscope），则根据 provider 设置默认模型
        if (model != null && !model.isBlank()) {
            // 检查是否是 provider 标识符
            for (LlmProviderType pt : LlmProviderType.values()) {
                if (pt.getConfigKey().equalsIgnoreCase(model)) {
                    // 是 provider 标识，需要解析为对应的模型名
                    return LlmProviderType.MINIMAX.equals(pt) ? "MiniMax-M2.7" : "qwen-plus";
                }
            }
            // 不是 provider 标识，可能是实际的模型名，直接返回
            return model;
        }
        // 没有模型名，使用 provider 默认
        return LlmProviderType.MINIMAX.equals(provider) ? "MiniMax-M2.7" : "qwen-plus";
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
