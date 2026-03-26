package com.liang.drugagent.agent.scene;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.chat.AgentChatContext;
import com.liang.drugagent.agent.prompt.AgentPrompt;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.request.agent.FileChatReq;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.orchestrator.TenderReviewToolOrchestrator;
import com.liang.drugagent.shared.domain.model.AgentExecutionResult;
import com.liang.drugagent.shared.domain.model.WorkflowRouteDecision;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.agent.utils.CompletableFutureUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Agent 场景服务。
 *
 * <p>统一负责"判断当前场景"以及"将请求分发给对应场景执行器"。
 *
 * <p>核心职责：
 * <ul>
 *   <li>基于 query、sceneHint、文件、上下文判断场景</li>
 *   <li>返回 {@link WorkflowRouteDecision}</li>
 *   <li>根据 scene 选择对应场景执行器</li>
 *   <li>返回统一 {@link AgentExecutionResult}</li>
 * </ul>
 *
 * <p>该服务合并了原 {@link com.liang.drugagent.agent.route.AgentRouteService}
 * 和 {@link com.liang.drugagent.agent.route.AgentSceneDispatcher} 的职责，
 * 简化调用链路，使 AgentChatService 更轻量。
 *
 * @author liangjiajian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSceneService {

    private static final String SYSTEM_PROMPT = AgentPrompt.SCENE_CLASSIFICATION;
    private static final double HIGH_CONFIDENCE = 0.9;
    private static final double MEDIUM_CONFIDENCE = 0.7;

    private final LlmService llmService;
    private final RouteConfig routeConfig;
    private final ObjectMapper objectMapper;
    private final TenderReviewToolOrchestrator tenderReviewToolOrchestrator;
    private final GeneralChatService generalChatService;

    // ==================== 公共入口 ====================

    /**
     * 执行完整场景判断与分发。
     *
     * @param context 执行上下文
     * @param req     对话请求
     * @return 场景执行结果，包含路由决策和执行结果
     */
    public AgentSceneExecution decideAndExecute(AgentChatContext context, AgentChatReq req) {
        log.info("[AgentSceneService] 开始场景判断与分发: sessionId={}", context.getSessionId());

        // 1. 执行路由判断
        WorkflowRouteDecision decision = routeDecision(req, context);

        // 2. 判断是否需要澄清
        if (shouldClarify(decision)) {
            String clarifyQuestion = generateClarificationQuestion(decision);
            log.info("[AgentSceneService] 需要澄清: sessionId={}, question={}", context.getSessionId(), clarifyQuestion);
            AgentExecutionResult clarifyResult = AgentExecutionResult.builder()
                    .success(false)
                    .scene(decision != null ? decision.getScene() : SceneEnum.UNKNOWN)
                    .errorMessage(clarifyQuestion)
                    .needsFallback(true)
                    .build();
            return AgentSceneExecution.builder()
                    .decision(decision)
                    .executionResult(clarifyResult)
                    .needsClarification(true)
                    .clarificationQuestion(clarifyQuestion)
                    .build();
        }

        // 3. 执行场景分发
        AgentExecutionResult executionResult = dispatch(context, decision, req);

        return AgentSceneExecution.builder()
                .decision(decision)
                .executionResult(executionResult)
                .needsClarification(false)
                .build();
    }

    /**
     * 执行文件上传场景的完整流程。
     *
     * @param context 执行上下文
     * @param req     文件对话请求
     * @return 场景执行结果
     */
    public AgentSceneExecution decideAndExecuteForFileChat(AgentChatContext context, FileChatReq req) {
        log.info("[AgentSceneService] 开始文件场景判断与分发: sessionId={}", context.getSessionId());

        // 1. 执行路由判断（基于文件场景）
        WorkflowRouteDecision decision = routeDecisionForFile(req, context);

        // 2. 执行场景分发
        AgentExecutionResult executionResult = dispatchForFileChat(context, decision, req);

        return AgentSceneExecution.builder()
                .decision(decision)
                .executionResult(executionResult)
                .needsClarification(false)
                .build();
    }

    /**
     * 流式分发。
     */
    public void dispatchStream(AgentChatContext context,
                                WorkflowRouteDecision decision,
                                AgentChatReq req,
                                SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("start").data(""));

            switch (decision.getScene()) {
                case TENDER_REVIEW -> {
                    AgentExecutionResult result = dispatchToTenderReview(context, decision);
                    emitter.send(SseEmitter.event().name("result").data(result.getAnswer()));
                }
                case UNKNOWN -> {
                    generalChatService.streamChat(req.getQuery(), context.getSessionId(), emitter);
                }
                default -> {
                    emitter.send(SseEmitter.event().name("result")
                            .data("该场景暂不支持流式输出"));
                }
            }

            emitter.send(SseEmitter.event().name("end").data(""));

        } catch (Exception e) {
            log.error("[AgentSceneService] 流式分发失败: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            } catch (IOException ex) {
                log.warn("[AgentSceneService] SSE发送错误失败", ex);
            }
        }
    }

    // ==================== 路由判断 ====================

    /**
     * 执行路由判断。
     */
    private WorkflowRouteDecision routeDecision(AgentChatReq req, AgentChatContext context) {
        RuleSignals ruleSignals = detectRuleSignal(req);
        RouteContext routeContext = enrichContext(req, ruleSignals);
        WorkflowRouteDecision explicitDecision = resolveExplicitDecision(req);
        if (explicitDecision != null) {
            context.setSceneType(explicitDecision.getScene());
            log.info("[AgentSceneService] 显式路由命中: scene={}, source={}, confidence={}",
                    explicitDecision.getScene(), explicitDecision.getSource(), explicitDecision.getConfidence());
            return explicitDecision;
        }

        WorkflowRouteDecision ruleDecision = buildDecisionFromRuleSignals(ruleSignals);
        if (canShortCircuitByRule(ruleDecision)) {
            context.setSceneType(ruleDecision.getScene());
            log.info("[AgentSceneService] 规则直达命中: scene={}, source={}, confidence={}, reason={}",
                    ruleDecision.getScene(), ruleDecision.getSource(), ruleDecision.getConfidence(), ruleDecision.getReason());
            return ruleDecision;
        }

        WorkflowRouteDecision llmDecision = null;
        if (shouldCallLlm(routeContext, ruleDecision)) {
            llmDecision = understandByLlm(routeContext, ruleSignals);
        }

        WorkflowRouteDecision finalDecision = arbitrate(ruleDecision, llmDecision);
        context.setSceneType(finalDecision.getScene());

        log.info("[AgentSceneService] 最终决策: scene={}, source={}, confidence={}",
                finalDecision.getScene(), finalDecision.getSource(), finalDecision.getConfidence());

        return finalDecision;
    }

    /**
     * 执行文件场景路由判断。
     */
    private WorkflowRouteDecision routeDecisionForFile(FileChatReq req, AgentChatContext context) {
        WorkflowRouteDecision explicitDecision = resolveExplicitDecisionForFile(req);
        if (explicitDecision != null) {
            context.setSceneType(explicitDecision.getScene());
            return explicitDecision;
        }

        // 文件场景下，直接根据文件数量判断
        if (req.getFiles() != null && req.getFiles().length >= 2) {
            return WorkflowRouteDecision.builder()
                    .scene(SceneEnum.TENDER_REVIEW)
                    .source("rule")
                    .reason("多文件上传，倾向文件比对类任务")
                    .confidence(0.88)
                    .requiresClarification(false)
                    .build();
        }

        if (req.getFiles() != null && req.getFiles().length == 1) {
            String filename = req.getFiles()[0].getOriginalFilename();
            if (isTenderFile(filename)) {
                return WorkflowRouteDecision.builder()
                        .scene(SceneEnum.TENDER_REVIEW)
                        .source("rule")
                        .reason("文件名含标书关键词")
                        .confidence(0.95)
                        .requiresClarification(false)
                        .build();
            }
        }

        // 默认降级
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.UNKNOWN)
                .source("fallback")
                .reason("无法确定处理场景")
                .confidence(0.0)
                .requiresClarification(false)
                .build();
    }

    private boolean shouldClarify(WorkflowRouteDecision decision) {
        if (decision != null && decision.isRequiresClarification()) {
            return true;
        }
        if (decision == null || decision.getScene() == SceneEnum.UNKNOWN) {
            return true;
        }
        Double confidence = decision.getConfidence();
        if (confidence == null || confidence < routeConfig.getConfidenceLowThreshold()) {
            return true;
        }
        return false;
    }

    private String generateClarificationQuestion(WorkflowRouteDecision decision) {
        if (decision != null && decision.getClarificationQuestion() != null
                && !decision.getClarificationQuestion().isBlank()) {
            return decision.getClarificationQuestion();
        }

        return switch (decision != null && decision.getScene() != null
                ? decision.getScene().name() : "UNKNOWN") {
            case "TENDER_REVIEW" -> "我理解您想要进行标书相关分析。请问您是想要：\n1）比对两份标书的相似度？\n2）检查标书是否存在围标嫌疑？\n3）其他标书审查需求？";
            case "CONTRACT_PRECHECK" -> "我理解您想要进行合同相关审核。请问您是想要：\n1）审核合同条款的风险？\n2）检查合同条款的合规性？\n3）其他合同相关需求？";
            case "RISK_ALERT" -> "我理解您想要进行风险分析。请问您是想要：\n1）分析药品/耗材的用量趋势？\n2）检测异常数据预警？\n3）生成统计分析报告？";
            default -> "抱歉，我目前无法确定您的具体需求。请告诉我您想要：\n1）比对标书文件\n2）审核合同条款\n3）分析药品/耗材风险数据\n或者直接描述您的具体需求";
        };
    }

    // ==================== 分发执行 ====================

    /**
     * 分发执行（同步）。
     */
    private AgentExecutionResult dispatch(AgentChatContext context,
                                          WorkflowRouteDecision decision,
                                          AgentChatReq req) {
        if (decision == null || decision.getScene() == null) {
            log.warn("[AgentSceneService] 路由决策为空，降级到通用对话");
            return dispatchToGeneralChat(context, req.getQuery());
        }

        SceneEnum scene = decision.getScene();

        log.info("[AgentSceneService] 开始分发场景: {}, sessionId={}",
                scene, context.getSessionId());

        return switch (scene) {
            case TENDER_REVIEW -> dispatchToTenderReview(context, decision);
            case CONTRACT_PRECHECK -> dispatchToContractPrecheck(context, decision);
            case RISK_ALERT -> dispatchToRiskAlert(context, decision);
            case UNKNOWN -> dispatchToGeneralChat(context, req.getQuery());
        };
    }

    /**
     * 分发执行（文件上传场景）。
     */
    private AgentExecutionResult dispatchForFileChat(AgentChatContext context,
                                                     WorkflowRouteDecision decision,
                                                     FileChatReq req) {
        if (decision == null || decision.getScene() == null) {
            log.warn("[AgentSceneService] 文件对话路由决策为空，执行降级");
            return AgentExecutionResult.failure(SceneEnum.UNKNOWN, "无法确定处理场景");
        }

        SceneEnum scene = decision.getScene();

        log.info("[AgentSceneService] 分发文件对话场景: {}, sessionId={}",
                scene, context.getSessionId());

        return switch (scene) {
            case TENDER_REVIEW -> dispatchToTenderReviewWithFiles(context, decision, req);
            case CONTRACT_PRECHECK -> dispatchToContractPrecheckWithFiles(context, decision, req);
            case RISK_ALERT -> dispatchToRiskAlert(context, decision);
            case UNKNOWN -> dispatchToGeneralChat(context, req.getQuery());
        };
    }

    private AgentExecutionResult dispatchToTenderReview(AgentChatContext context,
                                                         WorkflowRouteDecision decision) {
        log.info("[AgentSceneService] 分发到标书审查编排器");

        try {
            TenderReviewData tenderReviewData = extractTenderReviewData(context);

            if (tenderReviewData == null || tenderReviewData.getDocuments() == null
                    || tenderReviewData.getDocuments().isEmpty()) {
                log.warn("[AgentSceneService] 无可用的标书审查数据");
                return AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW, "缺少标书审查数据，请先上传标书文件");
            }

            AgentExecutionResult result = tenderReviewToolOrchestrator.orchestrate(
                    tenderReviewData, context.getQuery(), context);

            return result;

        } catch (Exception e) {
            log.error("[AgentSceneService] 标书审查分发失败: {}", e.getMessage(), e);
            return AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW, "标书审查执行失败: " + e.getMessage());
        }
    }

    private AgentExecutionResult dispatchToTenderReviewWithFiles(AgentChatContext context,
                                                                  WorkflowRouteDecision decision,
                                                                  FileChatReq req) {
        log.info("[AgentSceneService] 分发到标书审查编排器(带文件)");

        try {
            // 文件已通过 FileChatReq 传入，暂不处理文件解析逻辑
            // 实际应调用 TenderReviewPreparationService.prepareTenderReviewData

            return AgentExecutionResult.builder()
                    .success(false)
                    .scene(SceneEnum.TENDER_REVIEW)
                    .errorMessage("文件处理功能暂未开放")
                    .needsFallback(true)
                    .build();

        } catch (Exception e) {
            log.error("[AgentSceneService] 带文件的标书审查失败: {}", e.getMessage(), e);
            return AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW, "标书审查执行失败: " + e.getMessage());
        }
    }

    private AgentExecutionResult dispatchToContractPrecheck(AgentChatContext context,
                                                              WorkflowRouteDecision decision) {
        log.warn("[AgentSceneService] 合同预审暂未实现，降级到通用对话");
        return dispatchToGeneralChat(context, "请进行合同条款审核");
    }

    private AgentExecutionResult dispatchToContractPrecheckWithFiles(AgentChatContext context,
                                                                     WorkflowRouteDecision decision,
                                                                     FileChatReq req) {
        log.warn("[AgentSceneService] 合同预审(带文件)暂未实现");
        return AgentExecutionResult.failure(SceneEnum.CONTRACT_PRECHECK, "合同预审功能暂未开放");
    }

    private AgentExecutionResult dispatchToRiskAlert(AgentChatContext context,
                                                      WorkflowRouteDecision decision) {
        log.warn("[AgentSceneService] 风险预警暂未实现，降级到通用对话");
        return dispatchToGeneralChat(context, "请进行风险预警分析");
    }

    public AgentExecutionResult dispatchToGeneralChat(AgentChatContext context, String query) {
        log.info("[AgentSceneService] 分发到通用对话");

        try {
            String answer = generalChatService.chat(query, context.getSessionId());

            return AgentExecutionResult.builder()
                    .success(true)
                    .scene(SceneEnum.UNKNOWN)
                    .answer(answer)
                    .summary("通用对话")
                    .riskLevel("UNKNOWN")
                    .score(0)
                    .steps(List.of("问题理解", "知识检索", "回复生成"))
                    .needsFallback(false)
                    .build();

        } catch (Exception e) {
            log.error("[AgentSceneService] 通用对话分发失败: {}", e.getMessage(), e);
            return AgentExecutionResult.failure(SceneEnum.UNKNOWN, "通用对话执行失败: " + e.getMessage());
        }
    }

    private TenderReviewData extractTenderReviewData(AgentChatContext context) {
        if (context == null || context.getMetadata() == null) {
            return null;
        }

        Object rawData = context.getMetadata().get("tenderReviewData");
        if (rawData instanceof TenderReviewData) {
            return (TenderReviewData) rawData;
        }

        return null;
    }

    // ==================== 路由判断辅助方法 ====================

    private WorkflowRouteDecision resolveExplicitDecision(AgentChatReq req) {
        SceneEnum sceneHint = SceneEnum.fromHint(req.getSceneHint());
        if (sceneHint == null || sceneHint == SceneEnum.UNKNOWN) {
            return null;
        }

        return WorkflowRouteDecision.builder()
                .scene(sceneHint)
                .source("scene_hint")
                .reason("前端或上游显式指定场景")
                .confidence(1.0)
                .requiresClarification(false)
                .build();
    }

    private WorkflowRouteDecision resolveExplicitDecisionForFile(FileChatReq req) {
        SceneEnum sceneHint = SceneEnum.fromHint(req.getSceneHint());
        if (sceneHint == null || sceneHint == SceneEnum.UNKNOWN) {
            return null;
        }

        return WorkflowRouteDecision.builder()
                .scene(sceneHint)
                .source("scene_hint")
                .reason("前端或上游显式指定场景")
                .confidence(1.0)
                .requiresClarification(false)
                .build();
    }

    private RouteContext enrichContext(AgentChatReq req, RuleSignals ruleSignals) {
        List<String> fileNames = extractFileNames(req);

        List<String> availableScenes = Arrays.stream(SceneEnum.values())
                .filter(s -> s != SceneEnum.UNKNOWN)
                .map(SceneEnum::name)
                .collect(Collectors.toList());

        Map<String, Object> ruleSignalMap = null;
        if (ruleSignals != null && ruleSignals.scene() != null && ruleSignals.scene() != SceneEnum.UNKNOWN) {
            ruleSignalMap = new LinkedHashMap<>();
            ruleSignalMap.put("scene", ruleSignals.scene().name());
            ruleSignalMap.put("confidence", ruleSignals.confidence());
            ruleSignalMap.put("reason", ruleSignals.reason());
            ruleSignalMap.put("strong", ruleSignals.strong());
        }

        return new RouteContext(
                req.getQuery(),
                fileNames,
                fileNames.size(),
                req.getSceneHint(),
                ruleSignalMap,
                availableScenes,
                fileNames.size() > 0
        );
    }

    private RuleSignals detectRuleSignal(AgentChatReq req) {
        List<String> fileNames = extractFileNames(req);
        String query = req.getQuery() != null ? req.getQuery().toLowerCase() : "";

        // 1. 强规则：多文件且存在标书语义，直接认为是标书审查
        if (fileNames.size() >= 2) {
            if (fileNames.stream().anyMatch(this::isTenderFile)
                    || containsAny(query, "标书", "投标", "招标", "围标", "串标", "雷同", "查重", "对比")) {
                return new RuleSignals(SceneEnum.TENDER_REVIEW, 0.98, "多文件且存在标书语义", true);
            }
            return new RuleSignals(SceneEnum.TENDER_REVIEW, 0.88, "多文件上传，倾向文件比对类任务", false);
        }

        // 2. 强规则：单文件但文件名明显是标书
        if (fileNames.size() == 1 && isTenderFile(fileNames.get(0))) {
            return new RuleSignals(SceneEnum.TENDER_REVIEW, 0.95, "文件名含标书关键词", true);
        }

        // 3. 中规则：单文件更像文档审核，但需要结合文本进一步判断
        if (fileNames.size() == 1) {
            if (containsAny(query, "合同", "协议", "条款", "法务", "审核", "预审", "审查")) {
                return new RuleSignals(SceneEnum.CONTRACT_PRECHECK, 0.92, "单文件且文本含合同关键词", true);
            }
            return new RuleSignals(SceneEnum.CONTRACT_PRECHECK, 0.65, "单文件上传，倾向文档审核", false);
        }

        // 4. 纯文本关键词判断
        if (containsAny(query, "标书", "投标", "串标", "围标", "雷同", "查重", "相似", "对比")) {
            return new RuleSignals(SceneEnum.TENDER_REVIEW, 0.90, "文本含标书关键词", true);
        }
        if (containsAny(query, "合同", "协议", "条款", "法务", "审核", "预审", "审查")) {
            return new RuleSignals(SceneEnum.CONTRACT_PRECHECK, 0.90, "文本含合同关键词", true);
        }
        if (containsAny(query, "药品", "耗材", "预警", "异常", "用量", "趋势", "统计", "分析")) {
            return new RuleSignals(SceneEnum.RISK_ALERT, 0.85, "文本含风险分析关键词", false);
        }

        return RuleSignals.none();
    }

    private boolean shouldCallLlm(RouteContext context, WorkflowRouteDecision ruleDecision) {
        if (!routeConfig.isLlmEnabled()) {
            return false;
        }
        if (context == null) {
            return false;
        }
        if (ruleDecision == null) {
            return true;
        }
        Double confidence = ruleDecision.getConfidence();
        return confidence == null || confidence < routeConfig.getConfidenceThreshold();
    }

    private boolean canShortCircuitByRule(WorkflowRouteDecision ruleDecision) {
        if (ruleDecision == null || ruleDecision.getScene() == null || ruleDecision.getScene() == SceneEnum.UNKNOWN) {
            return false;
        }
        Double confidence = ruleDecision.getConfidence();
        return confidence != null && confidence >= routeConfig.getConfidenceThreshold();
    }

    private WorkflowRouteDecision understandByLlm(RouteContext context, RuleSignals ruleSignals) {
        String prompt = buildPrompt(context);

        log.debug("[AgentSceneService] 调用 LLM, query={}, fileCount={}", context.query(), context.fileCount());

        String rawResponse;
        try {
            rawResponse = callWithTimeout(prompt, SYSTEM_PROMPT, routeConfig.getLlmTimeout());
        } catch (TimeoutException e) {
            log.error("[AgentSceneService] LLM 调用超时");
            throw new RouteException("LLM 调用超时");
        } catch (Exception e) {
            log.error("[AgentSceneService] LLM 调用失败", e);
            throw new RouteException("LLM 调用失败: " + e.getMessage());
        }

        if (rawResponse == null || rawResponse.isBlank()) {
            throw new RouteException("LLM 返回为空");
        }

        return parseAndValidate(rawResponse);
    }

    private WorkflowRouteDecision arbitrate(WorkflowRouteDecision ruleDecision, WorkflowRouteDecision llmDecision) {
        if (ruleDecision == null && llmDecision == null) {
            return buildUnknownDecision("规则和 LLM 都无法决策");
        }
        if (ruleDecision == null) {
            return llmDecision;
        }
        if (llmDecision == null) {
            return finalizeRuleDecision(ruleDecision);
        }

        if (ruleDecision.getScene() == llmDecision.getScene()) {
            return WorkflowRouteDecision.builder()
                    .scene(ruleDecision.getScene())
                    .source("merged")
                    .reason("规则与 LLM 一致")
                    .confidence(Math.max(safeConfidence(ruleDecision), safeConfidence(llmDecision)))
                    .requiresClarification(false)
                    .raw(Map.of(
                            "ruleReason", defaultText(ruleDecision.getReason()),
                            "llmReason", defaultText(llmDecision.getReason())
                    ))
                    .build();
        }

        double ruleConf = safeConfidence(ruleDecision);
        double llmConf = safeConfidence(llmDecision);

        if (ruleConf >= routeConfig.getConfidenceThreshold() && ruleConf > llmConf) {
            return finalizeRuleDecision(ruleDecision);
        }
        if (llmConf >= routeConfig.getConfidenceThreshold() && llmConf > ruleConf) {
            return llmDecision;
        }

        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.UNKNOWN)
                .source("conflict")
                .reason(String.format("规则(%s)与LLM(%s)冲突，且都不足以直接执行",
                        ruleDecision.getScene(), llmDecision.getScene()))
                .confidence(Math.min(llmConf, ruleConf))
                .requiresClarification(true)
                .clarificationQuestion("系统检测到您可能想要执行多个操作。请确认您是想要：1）标书查重分析；2）合同预审；还是3）风险预警分析？")
                .raw(Map.of(
                        "ruleScene", ruleDecision.getScene().name(),
                        "ruleReason", defaultText(ruleDecision.getReason()),
                        "llmScene", llmDecision.getScene().name(),
                        "llmReason", defaultText(llmDecision.getReason())
                ))
                .build();
    }

    // ==================== 工具方法 ====================

    private List<String> extractFileNames(AgentChatReq req) {
        Map<String, Object> metadata = req.getMetadata();
        if (metadata == null) return List.of();

        Object rawFiles = metadata.get("uploadedFiles");
        if (!(rawFiles instanceof List<?> files)) return List.of();

        return files.stream()
                .filter(f -> f instanceof Map)
                .map(f -> (Map<?, ?>) f)
                .map(f -> f.get("filename") != null ? f.get("filename").toString() : "")
                .toList();
    }

    private boolean isTenderFile(String filename) {
        if (filename == null || filename.isBlank()) return false;
        return containsAny(filename.toLowerCase(), "标书", "投标", "招标", "围标", "串标");
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word.toLowerCase())) return true;
        }
        return false;
    }

    private String buildPrompt(RouteContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户查询：").append(context.query() != null ? context.query() : "（空）").append("\n");
        sb.append("上传文件数量：").append(context.fileCount()).append("\n");
        if (!context.uploadedFileNames().isEmpty()) {
            sb.append("上传文件名列表：\n");
            for (int i = 0; i < context.uploadedFileNames().size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(context.uploadedFileNames().get(i)).append("\n");
            }
        }
        if (context.sceneHint() != null && !context.sceneHint().isBlank()) {
            sb.append("上游指定场景：").append(context.sceneHint()).append("\n");
        }
        if (context.ruleSignals() != null && !context.ruleSignals().isEmpty()) {
            sb.append("已有规则信号：").append(context.ruleSignals()).append("\n");
        }
        sb.append("\n请根据以上信息，判断用户意图所属的业务场景。");
        return sb.toString();
    }

    private WorkflowRouteDecision parseAndValidate(String rawResponse) {
        try {
            String jsonStr = extractJson(rawResponse);
            JsonNode node = objectMapper.readTree(jsonStr);

            String sceneStr = node.path("scene").asText("UNKNOWN");
            double confidence = Math.max(0.0, Math.min(1.0, node.path("confidence").asDouble(0.5)));
            String reason = node.path("reason").asText("LLM 判断");
            boolean requiresClarification = node.path("requiresClarification").asBoolean(false);
            String clarificationQuestion = node.path("clarificationQuestion").asText("");

            SceneEnum scene;
            try {
                scene = SceneEnum.valueOf(sceneStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                scene = SceneEnum.UNKNOWN;
            }

            return WorkflowRouteDecision.builder()
                    .scene(scene)
                    .source("llm")
                    .reason(reason)
                    .confidence(confidence)
                    .requiresClarification(requiresClarification)
                    .clarificationQuestion(clarificationQuestion)
                    .build();

        } catch (Exception e) {
            log.error("[AgentSceneService] 解析 LLM 输出失败", e);
            throw new RouteException("解析 LLM 输出失败: " + e.getMessage());
        }
    }

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) trimmed = trimmed.substring(firstNewline + 1);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private String callWithTimeout(String prompt, String systemPrompt, long timeoutMs) throws TimeoutException {
        try {
            return CompletableFutureUtils.executeWithTimeout(
                    () -> llmService.chat(prompt, systemPrompt, "intent-routing"),
                    timeoutMs
            );
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException("LLM call timeout");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private WorkflowRouteDecision buildDecisionFromRuleSignals(RuleSignals ruleSignals) {
        if (ruleSignals == null || ruleSignals.scene() == null || ruleSignals.scene() == SceneEnum.UNKNOWN) {
            return null;
        }
        return WorkflowRouteDecision.builder()
                .scene(ruleSignals.scene())
                .source("rule")
                .reason(ruleSignals.reason())
                .confidence(ruleSignals.confidence())
                .requiresClarification(false)
                .raw(Map.of("strong", ruleSignals.strong()))
                .build();
    }

    private WorkflowRouteDecision finalizeRuleDecision(WorkflowRouteDecision ruleDecision) {
        if (ruleDecision == null) {
            return null;
        }
        return WorkflowRouteDecision.builder()
                .scene(ruleDecision.getScene())
                .source(ruleDecision.getSource())
                .reason(ruleDecision.getReason())
                .confidence(ruleDecision.getConfidence())
                .requiresClarification(false)
                .raw(ruleDecision.getRaw())
                .build();
    }

    private WorkflowRouteDecision buildUnknownDecision(String reason) {
        return WorkflowRouteDecision.builder()
                .scene(SceneEnum.UNKNOWN)
                .source("fallback")
                .reason(reason)
                .confidence(0.0)
                .requiresClarification(false)
                .build();
    }

    private double safeConfidence(WorkflowRouteDecision decision) {
        return decision != null && decision.getConfidence() != null ? decision.getConfidence() : 0.0;
    }

    private String defaultText(String text) {
        return text == null ? "" : text;
    }

    // ==================== 内部类 ====================

    public static class RouteException extends RuntimeException {
        public RouteException(String message) {
            super(message);
        }
    }

    private record RuleSignals(SceneEnum scene, double confidence, String reason, boolean strong) {
        public static RuleSignals none() {
            return new RuleSignals(SceneEnum.UNKNOWN, 0.0, "无规则信号", false);
        }
    }

    private record RouteContext(
            String query,
            List<String> uploadedFileNames,
            int fileCount,
            String sceneHint,
            Map<String, Object> ruleSignals,
            List<String> availableScenes,
            boolean hasAttachments
    ) {}

    @ConfigurationProperties(prefix = "agent.routing")
    @Configuration
    public static class RouteConfig {
        private boolean llmEnabled = true;
        private int llmTimeout = 10000;
        private double confidenceThreshold = 0.75;
        private double confidenceLowThreshold = 0.5;

        public boolean isLlmEnabled() { return llmEnabled; }
        public void setLlmEnabled(boolean llmEnabled) { this.llmEnabled = llmEnabled; }
        public int getLlmTimeout() { return llmTimeout; }
        public void setLlmTimeout(int llmTimeout) { this.llmTimeout = llmTimeout; }
        public double getConfidenceThreshold() { return confidenceThreshold; }
        public void setConfidenceThreshold(double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }
        public double getConfidenceLowThreshold() { return confidenceLowThreshold; }
        public void setConfidenceLowThreshold(double confidenceLowThreshold) { this.confidenceLowThreshold = confidenceLowThreshold; }
    }

    // ==================== 通用对话服务 ====================

    @lombok.RequiredArgsConstructor
    public static class GeneralChatService {

        private final LlmService llmService;

        private static final String SYSTEM_PROMPT = """
                你是一个专业的医疗监管AI助手，负责回答关于药品监管、医疗器械监管、标书审查、合同审核等相关问题。

                请用专业、清晰的语言回答用户的问题。如果不确定答案，请如实告知用户。
                """;

        public String chat(String query, String sessionId) {
            try {
                String prompt = buildPrompt(query, sessionId);
                return llmService.chat(prompt, SYSTEM_PROMPT, "general-chat");
            } catch (Exception e) {
                throw new RuntimeException("通用对话失败: " + e.getMessage(), e);
            }
        }

        public void streamChat(String query, String sessionId, SseEmitter emitter) {
            try {
                String prompt = buildPrompt(query, sessionId);
                String response = llmService.chat(prompt, SYSTEM_PROMPT, "general-chat");
                emitter.send(SseEmitter.event().name("result").data(response));
            } catch (Exception e) {
                throw new RuntimeException("流式对话失败: " + e.getMessage(), e);
            }
        }

        private String buildPrompt(String query, String sessionId) {
            StringBuilder sb = new StringBuilder();
            sb.append("用户问题：").append(query != null ? query : "（空）").append("\n\n");
            return sb.toString();
        }
    }

    // ==================== AgentSceneExecution DTO ====================

    /**
     * 场景执行结果。
     *
     * <p>包含路由决策和执行结果，用于在 AgentChatService 编排层与下游执行器之间传递完整信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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
