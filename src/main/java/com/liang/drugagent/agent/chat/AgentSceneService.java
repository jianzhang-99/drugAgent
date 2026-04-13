package com.liang.drugagent.agent.chat;

import com.liang.drugagent.agent.common.entity.ChatMessage;
import com.liang.drugagent.agent.prompt.shared.base.SharedBasePrompt;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.contextcache.DashScopeContextCacheService;
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
import com.liang.drugagent.shared.intent.IntentDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

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
    private final IntentDetectionService intentDetectionService;
    private final DashScopeContextCacheService dashScopeContextCacheService;

    @Value("${aliyun.dashscope.context-cache.enabled:true}")
    private boolean dashScopeContextCacheEnabled;

    @Value("${aliyun.dashscope.context-cache.min-system-prompt-length:1200}")
    private int dashScopeContextCacheMinSystemPromptLength;

    @Value("${agent.general-chat.history-window-size:8}")
    private int generalChatHistoryWindowSize;

    @Value("${llm.minimax-enabled:false}")
    private boolean minimaxEnabled;

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
        long totalStartTime = System.currentTimeMillis();

        try {
            // 1. 路由决策
            long routeStartTime = System.currentTimeMillis();
            WorkflowRouteDecision decision = decideRoute(context, req);
            long routeCostMs = System.currentTimeMillis() - routeStartTime;
            context.setSceneType(decision.getScene());
            log.info("[AgentSceneService] 路由决策完成 - routeCostMs={}, scene={}, source={}",
                    routeCostMs, decision.getScene(), decision.getSource());

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
            result.setGeneratedTitle(generateTenderReviewTitle(context, result));
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
    private String generateTenderReviewTitle(AgentChatContext context, AgentExecutionResult result) {
        List<String> documentNames = collectTenderDocumentNames(context, result);
        if (!documentNames.isEmpty()) {
            String commonProjectName = extractCommonProjectName(documentNames);
            if (commonProjectName != null) {
                return commonProjectName + "标书比对";
            }

            String primaryName = simplifyTenderDocumentName(documentNames.get(0));
            if (documentNames.size() == 2) {
                String secondaryName = simplifyTenderDocumentName(documentNames.get(1));
                if (!primaryName.equals(secondaryName)) {
                    return buildTitle(primaryName + "与" + secondaryName + "比对", 28);
                }
            }
            if (documentNames.size() > 1) {
                return buildTitle(primaryName + "等" + documentNames.size() + "份标书审查", 28);
            }
            return buildTitle(primaryName + "标书审查", 28);
        }

        String queryTitle = buildTenderQueryTitle(context != null ? context.getQuery() : null);
        if (queryTitle != null) {
            return queryTitle;
        }
        return "标书审查";
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
    public WorkflowRouteDecision decideRoute(AgentChatContext context, AgentChatReq req) {
        String query = context.getQuery();

        // ========== 第 0 层：明显通用问题（最高优先级，无需 LLM）==========
        // 即使有文件引用或 session 上下文，只要是明显通用问题就直接拦截
        if (isObviouslyGeneralQuery(query)) {
            log.info("[AgentSceneService] 【通用问题拦截】检测到明显通用问题，直接走 DEFAULT");
            return WorkflowRouteDecision.builder()
                    .scene(SceneEnum.DEFAULT)
                    .source("obvious-general")
                    .reason("明显通用问题，不分发到业务场景")
                    .confidence(1.0)
                    .requiresClarification(false)
                    .build();
        }

        // ========== 第一层：显式信号（最强，无需 LLM）==========

        // 1.1 有新上传文件 -> 强命中进标书审查
        if (hasNewUploadedFiles(req)) {
            log.info("[AgentSceneService] 【显式信号】检测到新上传文件，直接进标书审查");
            return WorkflowRouteDecision.builder()
                    .scene(SceneEnum.TENDER_REVIEW)
                    .source("explicit-file")
                    .reason("检测到新上传文件，强制路由到标书审查")
                    .confidence(1.0)
                    .requiresClarification(false)
                    .build();
        }

        // 1.2 有历史文件ID引用 -> 交给后续规则判断，不强制路由
        if (hasFileIdReference(req)) {
            log.info("[AgentSceneService] 【显式信号】有历史文件ID引用，交给后续规则判断");
        }

        // 1.3 sceneHint 明确指定场景 -> 强命中
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

        // 1.4 会话上下文已有标书审查场景 -> 直接复用场景，不走弱规则澄清
        // 注意：通用问题已在第 0 层拦截，不会到达此处
        if (context.getSession() != null) {
            String sessionScene = context.getSession().getLastScene();
            List<String> contextFileIds = context.getFileIds();
            log.info("[AgentSceneService] 【会话上下文检查】sessionId={}, lastScene={}, fileIds.size={}",
                    context.getSessionId(), sessionScene, contextFileIds != null ? contextFileIds.size() : 0);
            if ("tender_review".equalsIgnoreCase(sessionScene) || "TENDER_REVIEW".equalsIgnoreCase(sessionScene)) {
                log.info("[AgentSceneService] 【会话上下文】检测到标书审查会话，直接路由到标书审查场景");
                return WorkflowRouteDecision.builder()
                        .scene(SceneEnum.TENDER_REVIEW)
                        .source("session-context")
                        .reason("会话上下文为标书审查场景，复用场景")
                        .confidence(0.92)
                        .requiresClarification(false)
                        .build();
            }
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

        // ========== 第三层：LLM 意图识别兜底 ==========

        // 没有强/弱规则命中，使用 tongyi-intent-detect-v3 做意图识别
        log.info("[AgentSceneService] 【未命中】调用意图识别模型 tongyi-intent-detect-v3 做兜底判断");
        SceneEnum detectedScene = intentDetectionService.detectIntent(query);

        if (detectedScene != SceneEnum.DEFAULT) {
            log.info("[AgentSceneService] 【意图识别】检测到业务场景: {}", detectedScene);
            return WorkflowRouteDecision.builder()
                    .scene(detectedScene)
                    .source("intent-model")
                    .reason("tongyi-intent-detect-v3 意图识别结果: " + detectedScene.name())
                    .confidence(0.7)
                    .requiresClarification(false)
                    .build();
        }

        // 意图识别也是 DEFAULT，说明确实是普通对话
        log.info("[AgentSceneService] 【意图识别】识别为普通对话，走 DEFAULT 场景");
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.DEFAULT)
                .source("intent-model")
                .reason("tongyi-intent-detect-v3 意图识别结果: DEFAULT")
                .confidence(0.5)
                .requiresClarification(false)
                .build();
    }

    /**
     * 判断是否为明显的通用对话问题，与业务场景无关。
     *
     * <p>用于在 session-context 场景复用时过滤掉"你是什么模型""你能做什么"等与业务无关的通用问题，
     * 避免这类问题被误路由到标书审查场景。</p>
     */
    private boolean isObviouslyGeneralQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String lowerQuery = query.toLowerCase().trim();
        // 明显的模型/能力咨询
        if (containsAny(lowerQuery, "你是什么模型", "你叫什么", "你是谁", "你能做什么",
                "你有什么功能", "你是哪个", "用的什么模型", "什么大模型",
                "介绍一下你自己", "你会什么", "你能干什么", "你是ai", "你是claude",
                "你是gpt", "你是qwen", "你是通义", "你是文心",
                "你还能做什么", "还能做什么", "能做些什么", "有什么能力", "你的功能", "有哪些功能")) {
            return true;
        }

        // 兼容用户省略主语的常见说法，例如“是什么模型”“用的是啥模型”
        if (lowerQuery.contains("模型")) {
            return containsAny(lowerQuery, "是什么模型", "啥模型", "什么模型", "哪个模型",
                    "模型是什么", "模型是啥", "用什么模型", "用的是啥模型", "用的是哪个模型",
                    "背后是什么模型", "基于什么模型", "底层模型", "大模型是什么");
        }

        return false;
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
     * 检查请求中是否有新上传文件（MultipartFile）。
     */
    private boolean hasNewUploadedFiles(AgentChatReq req) {
        return req.getFiles() != null && req.getFiles().length > 0;
    }

    /**
     * 检查请求中是否有历史文件ID引用。
     */
    private boolean hasFileIdReference(AgentChatReq req) {
        return req.getFileIds() != null && !req.getFileIds().isEmpty();
    }

    /**
     * 分发到通用对话。
     */
    private AgentSceneExecution dispatchToGeneralChat(AgentChatContext context, String query,
                                                       WorkflowRouteDecision decision) {
        log.info("[AgentSceneService] 分发到通用对话");
        long totalStartTime = System.currentTimeMillis();

        try {
            // 1. 知识检索增强（仅检索不生成答案，避免双LLM调用）
            long ragStartTime = System.currentTimeMillis();
            String orgId = extractOrgId(context);
            RagOutcome ragOutcome = knowledgeRetrievalTool.search(query, orgId, null, null, null);
            long ragRetrieveCostMs = System.currentTimeMillis() - ragStartTime;

            // 2. 判断检索结果，决定是否使用 RAG 上下文
            boolean useRagContext = "ANSWERED".equals(ragOutcome.getDecision())
                    && ragOutcome.getEvidenceList() != null
                    && !ragOutcome.getEvidenceList().isEmpty();

            if (useRagContext) {
                log.info("[AgentSceneService] 知识检索命中，使用 RAG 增强回答 - ragRetrieveCostMs={}, evidenceCount={}",
                        ragRetrieveCostMs, ragOutcome.getEvidenceList().size());
            } else {
                log.info("[AgentSceneService] 知识检索未命中，使用纯 LLM 回答 - ragRetrieveCostMs={}, decision={}",
                        ragRetrieveCostMs, ragOutcome.getDecision());
            }

            // 3. 构建增强后的 system prompt
            String systemPrompt = buildEnhancedSystemPrompt(ragOutcome, useRagContext);

            // 4. 调用 LLM 获取回答
            long answerStartTime = System.currentTimeMillis();
            GeneralChatResult chatResult = generalChatWithTitle(context, systemPrompt);
            long finalAnswerCostMs = System.currentTimeMillis() - answerStartTime;

            long totalCostMs = System.currentTimeMillis() - totalStartTime;
            log.info("[AgentSceneService] 通用对话完成 - totalCostMs={}, ragRetrieveCostMs={}, finalAnswerCostMs={}, useRagContext={}",
                    totalCostMs, ragRetrieveCostMs, finalAnswerCostMs, useRagContext);

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
     * 真流式通用对话入口（绕过同步 Execution 框架，直接返回 Flux）。
     */
    public Flux<String> streamGeneralChat(AgentChatContext context) {
        log.info("[AgentSceneService] 分发到真流式通用对话");

        try {
            // 1. 知识检索增强
            String orgId = extractOrgId(context);
            RagOutcome ragOutcome = knowledgeRetrievalTool.search(context.getQuery(), orgId, null, null, null);
            boolean useRagContext = "ANSWERED".equals(ragOutcome.getDecision())
                    && ragOutcome.getEvidenceList() != null
                    && !ragOutcome.getEvidenceList().isEmpty();

            // 2. 构建增强后的 system prompt
            String systemPrompt = buildEnhancedSystemPrompt(ragOutcome, useRagContext);

            // 3. 构建请求
            String query = context.getQuery();
            String sessionId = context.getSessionId();
            String model = context.getModel();
            LlmProviderType provider = resolveProviderType(model);
            String effectiveModel = resolveEffectiveModel(model, provider);
            
            String effectiveSystemPrompt = systemPrompt;
            List<LlmRequest.ChatMessage> messages = List.of(LlmRequest.ChatMessage.builder()
                    .role("user")
                    .content(query)
                    .build());

            if (LlmProviderType.DASHSCOPE.equals(provider)) {
                effectiveSystemPrompt = buildGeneralChatSystemPrompt(context, systemPrompt);
                messages = buildGeneralChatMessages(context, query);
            }

            LlmRequest request = LlmRequest.builder()
                    .provider(provider)
                    .model(effectiveModel)
                    .sessionId(sessionId)
                    .systemPrompt(effectiveSystemPrompt)
                    .messages(messages)
                    .stream(true)
                    .build();

            // 4. 调用 LLM 真流式
            return llmService.streamChat(request)
                    .map(r -> r.getContent() != null ? r.getContent() : "");

        } catch (Exception e) {
            log.error("[AgentSceneService] 真流式通用对话预处理失败: {}", e.getMessage(), e);
            return Flux.just("处理失败，请稍后重试: " + e.getMessage());
        }
    }

    /**
     * 通用对话结果（含回答和标题）。
     */
    private record GeneralChatResult(String answer, String title) {}

    /**
     * 通用对话处理（同时生成标题）。
     */
    private GeneralChatResult generalChatWithTitle(AgentChatContext context) {
        return generalChatWithTitle(context, SharedBasePrompt.GENERAL_CHAT);
    }

    /**
     * 通用对话处理（同时生成标题，支持自定义 system prompt）。
     */
    private GeneralChatResult generalChatWithTitle(AgentChatContext context, String systemPrompt) {
        try {
            String query = context.getQuery();
            String sessionId = context.getSessionId();
            String model = context.getModel();
            LlmProviderType provider = resolveProviderType(model);
            // 根据 provider 解析正确的模型名（前端传的是 provider 标识，不是模型名）
            String effectiveModel = resolveEffectiveModel(model, provider);
            String effectiveSystemPrompt = systemPrompt;
            List<LlmRequest.ChatMessage> messages = List.of(LlmRequest.ChatMessage.builder()
                    .role("user")
                    .content(query)
                    .build());

            if (LlmProviderType.DASHSCOPE.equals(provider)) {
                effectiveSystemPrompt = buildGeneralChatSystemPrompt(context, systemPrompt);
                messages = buildGeneralChatMessages(context, query);
            }

            LlmRequest request = LlmRequest.builder()
                    .provider(provider)
                    .model(effectiveModel)
                    .sessionId(sessionId)
                    .systemPrompt(effectiveSystemPrompt)
                    .messages(messages)
                    .build();
            LlmResponse llmResponse = callGeneralChatModel(request, provider, effectiveSystemPrompt);
            if (!Boolean.TRUE.equals(llmResponse.getSuccess()) || llmResponse.getContent() == null) {
                log.error("[AgentSceneService] 通用对话失败: provider={}, success={}, errorMessage={}",
                        provider, llmResponse.getSuccess(), llmResponse.getErrorMessage());
                throw new RuntimeException("LLM调用失败: " + llmResponse.getErrorMessage());
            }
            String answer = llmResponse.getContent();

            // 使用 query 截断作为默认标题，不等待 LLM 生成
            // LLM 回答中不要包含【会话标题】标记，避免解析混乱
            String title = generateDefaultTitle(query);

            return new GeneralChatResult(answer, title);
        } catch (Exception e) {
            throw new RuntimeException("通用对话失败: " + e.getMessage(), e);
        }
    }

    private LlmResponse callGeneralChatModel(LlmRequest request, LlmProviderType provider, String systemPrompt) {
        if (LlmProviderType.DASHSCOPE.equals(provider)
                && dashScopeContextCacheEnabled
                && systemPrompt != null
                && systemPrompt.length() >= dashScopeContextCacheMinSystemPromptLength) {
            LlmResponse cachedResponse = dashScopeContextCacheService.chatWithSessionCache(request, request.getSessionId());
            if (cachedResponse != null && Boolean.TRUE.equals(cachedResponse.getSuccess())) {
                log.info("[AgentSceneService] 通用对话命中 DashScope Context Cache - sessionId={}", request.getSessionId());
                return cachedResponse;
            }
            log.warn("[AgentSceneService] DashScope Context Cache 未命中或调用失败，降级到普通对话 - sessionId={}",
                    request.getSessionId());
        }
        return llmService.chat(request);
    }

    private String buildGeneralChatSystemPrompt(AgentChatContext context, String baseSystemPrompt) {
        if (context.getRecentSummary() == null || context.getRecentSummary().isBlank()) {
            return baseSystemPrompt;
        }

        StringBuilder promptBuilder = new StringBuilder(baseSystemPrompt);
        promptBuilder.append("\n\n【最近会话摘要】\n");
        promptBuilder.append(context.getRecentSummary());
        promptBuilder.append("\n请在回复时参考上述摘要，保持与当前会话连续，但不要虚构未提及的事实。");
        return promptBuilder.toString();
    }

    private List<LlmRequest.ChatMessage> buildGeneralChatMessages(AgentChatContext context, String query) {
        List<LlmRequest.ChatMessage> messages = new ArrayList<>();
        List<ChatMessage> historyMessages = context.getHistoryMessages();

        if (historyMessages != null && !historyMessages.isEmpty()) {
            int startIndex = Math.max(0, historyMessages.size() - generalChatHistoryWindowSize);
            for (int i = startIndex; i < historyMessages.size(); i++) {
                ChatMessage historyMessage = historyMessages.get(i);
                if (historyMessage == null
                        || historyMessage.getContent() == null
                        || historyMessage.getContent().isBlank()
                        || "assistant_result_card".equalsIgnoreCase(historyMessage.getType())) {
                    continue;
                }

                String role = resolveHistoryRole(historyMessage.getRole());
                if (role == null) {
                    continue;
                }

                messages.add(LlmRequest.ChatMessage.builder()
                        .role(role)
                        .content(historyMessage.getContent())
                        .build());
            }
        }

        if (shouldAppendCurrentQuery(messages, query)) {
            messages.add(LlmRequest.ChatMessage.builder()
                    .role("user")
                    .content(query)
                    .build());
        }

        return messages;
    }

    private boolean shouldAppendCurrentQuery(List<LlmRequest.ChatMessage> messages, String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        if (messages.isEmpty()) {
            return true;
        }

        LlmRequest.ChatMessage lastMessage = messages.get(messages.size() - 1);
        return !"user".equalsIgnoreCase(lastMessage.getRole())
                || !query.trim().equals(lastMessage.getContent() != null ? lastMessage.getContent().trim() : null);
    }

    private String resolveHistoryRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        if ("assistant".equalsIgnoreCase(role)) {
            return "assistant";
        }
        if ("user".equalsIgnoreCase(role)) {
            return "user";
        }
        return null;
    }

    /**
     * 生成默认标题（使用 query 截断）。
     */
    private String generateDefaultTitle(String query) {
        if (query == null || query.isBlank()) {
            return "新对话";
        }
        // 截取前20个字符作为标题
        String title = query.length() > 20 ? query.substring(0, 20) : query;
        return title;
    }

    private List<String> collectTenderDocumentNames(AgentChatContext context, AgentExecutionResult result) {
        List<String> documentNames = new ArrayList<>();
        if (result != null && result.getDocumentNames() != null) {
            documentNames.addAll(result.getDocumentNames().stream()
                    .filter(name -> name != null && !name.isBlank())
                    .toList());
        }
        if (documentNames.isEmpty() && context != null && context.getUploadedFiles() != null) {
            documentNames.addAll(context.getUploadedFiles().stream()
                    .map(file -> file != null ? file.getFileName() : null)
                    .filter(name -> name != null && !name.isBlank())
                    .toList());
        }
        return documentNames;
    }

    private String extractCommonProjectName(List<String> documentNames) {
        if (documentNames == null || documentNames.size() < 2) {
            return null;
        }
        String commonPrefix = null;
        for (String documentName : documentNames) {
            String normalizedName = simplifyTenderDocumentName(documentName);
            if (normalizedName.isBlank()) {
                continue;
            }
            if (commonPrefix == null) {
                commonPrefix = normalizedName;
                continue;
            }
            commonPrefix = commonPrefix(commonPrefix, normalizedName);
            commonPrefix = trimTrailingSeparator(commonPrefix);
            if (commonPrefix.length() < 4) {
                return null;
            }
        }
        if (commonPrefix == null) {
            return null;
        }

        String refinedName = commonPrefix
                .replaceAll("(商务标|技术标|报价标|投标文件|响应文件|招标文件|副本|终稿|最终版|完整版)+$", "")
                .replaceAll("[\\s\\-_（(]+$", "")
                .trim();

        if (refinedName.length() < 4 || isGenericTenderName(refinedName)) {
            return null;
        }
        return buildTitle(refinedName, 18);
    }

    private String simplifyTenderDocumentName(String documentName) {
        if (documentName == null || documentName.isBlank()) {
            return "";
        }
        String simplifiedName = documentName.trim()
                .replaceAll("\\.[A-Za-z0-9]{1,6}$", "")
                .replaceAll("[_]+", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("(（|\\()(副本|终稿|最终版|定稿)(）|\\))", "")
                .trim();
        return buildTitle(simplifiedName, 18);
    }

    private boolean isGenericTenderName(String name) {
        return "投标文件".equals(name)
                || "标书".equals(name)
                || "商务标".equals(name)
                || "技术标".equals(name)
                || "招标文件".equals(name)
                || "响应文件".equals(name);
    }

    private String commonPrefix(String first, String second) {
        int maxLength = Math.min(first.length(), second.length());
        int index = 0;
        while (index < maxLength && first.charAt(index) == second.charAt(index)) {
            index++;
        }
        return first.substring(0, index);
    }

    private String trimTrailingSeparator(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\s\\-_/（(]+$", "").trim();
    }

    private String buildTenderQueryTitle(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String normalizedQuery = query.trim()
                .replaceAll("^(帮我|请帮我|麻烦帮我|想请你|请你|帮忙)?(看看|分析一下|审查一下|审查|检查一下|检查)?", "")
                .replaceAll("(这|这几|这两|这三)?份?(标书|投标文件|招标文件)", "")
                .replaceAll("(是否|有无)?(存在)?", "")
                .replaceAll("(围标|串标|雷同|风险)", "")
                .replaceAll("[？?。!！,， ]+", "")
                .trim();

        if (!normalizedQuery.isBlank() && normalizedQuery.length() > 1 && !"有".equals(normalizedQuery)) {
            return buildTitle(normalizedQuery + "标书审查", 24);
        }

        if (query.contains("围标") || query.contains("串标")) {
            return "围串标风险审查";
        }
        if (query.contains("雷同")) {
            return "标书雷同审查";
        }
        return "标书审查";
    }

    private String buildTitle(String title, int maxLength) {
        if (title == null || title.isBlank()) {
            return "新对话";
        }
        String normalizedTitle = title.trim().replaceAll("\\s+", " ");
        if (normalizedTitle.length() <= maxLength) {
            return normalizedTitle;
        }
        return normalizedTitle.substring(0, maxLength);
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
        String normalizedModel = normalizeModel(model);
        if (normalizedModel != null) {
            if (isProviderConfigKey(normalizedModel)) {
                return defaultModelFor(provider);
            }
            return normalizedModel;
        }
        return defaultModelFor(provider);
    }

    private LlmProviderType resolveProviderType(String model) {
        String normalizedModel = normalizeModel(model);
        if (normalizedModel == null) {
            return LlmProviderType.DASHSCOPE;
        }
        if (isProviderConfigKey(normalizedModel)) {
            return normalizeProviderType(LlmProviderType.fromConfigKey(normalizedModel));
        }
        if (looksLikeDashScopeModel(normalizedModel)) {
            return LlmProviderType.DASHSCOPE;
        }
        if (looksLikeMiniMaxModel(normalizedModel)) {
            return normalizeProviderType(LlmProviderType.MINIMAX);
        }
        return LlmProviderType.DASHSCOPE;
    }

    private boolean isProviderConfigKey(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        for (LlmProviderType type : LlmProviderType.values()) {
            if (type.getConfigKey().equalsIgnoreCase(model)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeDashScopeModel(String model) {
        String lower = model.toLowerCase();
        return lower.startsWith("qwen")
                || lower.startsWith("tongyi")
                || lower.startsWith("qwq")
                || lower.startsWith("qvq")
                || lower.startsWith("text-embedding");
    }

    private boolean looksLikeMiniMaxModel(String model) {
        String lower = model.toLowerCase();
        return lower.startsWith("minimax")
                || lower.startsWith("abab");
    }

    private String defaultModelFor(LlmProviderType provider) {
        return LlmProviderType.MINIMAX.equals(provider) ? "MiniMax-M2.7-highspeed" : "qwen-plus";
    }

    private LlmProviderType normalizeProviderType(LlmProviderType providerType) {
        if (!minimaxEnabled && LlmProviderType.MINIMAX.equals(providerType)) {
            log.warn("[AgentSceneService] MiniMax 当前已被临时屏蔽，自动切换到 DashScope");
            return LlmProviderType.DASHSCOPE;
        }
        return providerType;
    }

    private String normalizeModel(String model) {
        if (model == null) {
            return null;
        }
        String trimmed = model.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
