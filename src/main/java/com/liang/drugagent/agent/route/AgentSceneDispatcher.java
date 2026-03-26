package com.liang.drugagent.agent.route;

import com.liang.drugagent.agent.chat.AgentChatContext;
import com.liang.drugagent.agent.preparation.TenderReviewPreparationService;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.tool.TenderReviewToolOrchestrator;
import com.liang.drugagent.shared.domain.model.AgentExecutionResult;
import com.liang.drugagent.shared.domain.model.WorkflowRouteDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Agent 场景分发器。
 *
 * <p>职责：
 * <ul>
 *   <li>根据 WorkflowRouteDecision 选择正确执行器</li>
 *   <li>把通用主流程中的"分发逻辑"从 AgentChatService 拆出</li>
 * </ul>
 *
 * <p>支持的场景分发：
 * <ul>
 *   <li>TENDER_REVIEW -> TenderReviewToolOrchestrator</li>
 *   <li>UNKNOWN -> 通用对话降级</li>
 *   <li>其他场景 -> 对应 Orchestrator（待扩展）</li>
 * </ul>
 *
 * @author liangjiajian
 * @see AgentRouteService
 * @see TenderReviewToolOrchestrator
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSceneDispatcher {

    private final TenderReviewToolOrchestrator tenderReviewToolOrchestrator;
    private final TenderReviewPreparationService tenderReviewPreparationService;
    private final GeneralChatService generalChatService;

    /**
     * 分发执行（同步）。
     */
    public AgentExecutionResult dispatch(AgentChatContext context,
                                         WorkflowRouteDecision decision,
                                         AgentChatReq req) {
        if (decision == null || decision.getScene() == null) {
            log.warn("[AgentSceneDispatcher] 路由决策为空，降级到通用对话");
            return dispatchToGeneralChat(context, req.getQuery());
        }

        SceneEnum scene = decision.getScene();

        log.info("[AgentSceneDispatcher] 开始分发场景: {}, sessionId={}",
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
    public AgentExecutionResult dispatchForFileChat(AgentChatContext context,
                                                    WorkflowRouteDecision decision,
                                                    FileChatReq req) {
        if (decision == null || decision.getScene() == null) {
            log.warn("[AgentSceneDispatcher] 文件对话路由决策为空，执行降级");
            return AgentExecutionResult.failure(SceneEnum.UNKNOWN, "无法确定处理场景");
        }

        SceneEnum scene = decision.getScene();

        log.info("[AgentSceneDispatcher] 分发文件对话场景: {}, sessionId={}",
                scene, context.getSessionId());

        return switch (scene) {
            case TENDER_REVIEW -> dispatchToTenderReviewWithFiles(context, decision, req);
            case CONTRACT_PRECHECK -> dispatchToContractPrecheckWithFiles(context, decision, req);
            case RISK_ALERT -> dispatchToRiskAlert(context, decision);
            case UNKNOWN -> dispatchToGeneralChat(context, req.getQuery());
        };
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
            log.error("[AgentSceneDispatcher] 流式分发失败: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            } catch (IOException ex) {
                log.warn("[AgentSceneDispatcher] SSE发送错误失败", ex);
            }
        }
    }

    // ==================== 场景分发方法 ====================

    private AgentExecutionResult dispatchToTenderReview(AgentChatContext context,
                                                        WorkflowRouteDecision decision) {
        log.info("[AgentSceneDispatcher] 分发到标书审查编排器");

        try {
            TenderReviewData tenderReviewData = extractTenderReviewData(context);

            if (tenderReviewData == null || tenderReviewData.getDocuments() == null
                    || tenderReviewData.getDocuments().isEmpty()) {
                log.warn("[AgentSceneDispatcher] 无可用的标书审查数据");
                return AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW, "缺少标书审查数据，请先上传标书文件");
            }

            TenderReviewToolOrchestrator.OrchestrationResult orchResult =
                    tenderReviewToolOrchestrator.orchestrateWithData(context, tenderReviewData);

            if (orchResult.needsFallback()) {
                return AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW, orchResult.getErrorMessage());
            }

            return AgentExecutionResult.builder()
                    .success(true)
                    .scene(SceneEnum.TENDER_REVIEW)
                    .answer(orchResult.getPolishedAnswer())
                    .summary(orchResult.getRawResult() != null ? orchResult.getRawResult().summary() : null)
                    .riskLevel(orchResult.getRiskLevel())
                    .score(orchResult.getScore())
                    .report(orchResult.getReport())
                    .evidenceList(orchResult.getEvidenceList())
                    .steps(orchResult.getSteps())
                    .caseId(orchResult.getCaseId())
                    .executionTimeMs(orchResult.getExecutionTimeMs())
                    .needsFallback(false)
                    .build();

        } catch (Exception e) {
            log.error("[AgentSceneDispatcher] 标书审查分发失败: {}", e.getMessage(), e);
            return AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW, "标书审查执行失败: " + e.getMessage());
        }
    }

    private AgentExecutionResult dispatchToTenderReviewWithFiles(AgentChatContext context,
                                                                   WorkflowRouteDecision decision,
                                                                   FileChatReq req) {
        log.info("[AgentSceneDispatcher] 分发到标书审查编排器(带文件)");

        try {
            TenderReviewData tenderReviewData = tenderReviewPreparationService.prepareTenderReviewData(
                    context,
                    req.getFiles(),
                    req.getSubmittedBy()
            );

            if (tenderReviewData == null || tenderReviewData.getDocuments() == null
                    || tenderReviewData.getDocuments().isEmpty()) {
                log.warn("[AgentSceneDispatcher] 准备标书审查数据失败");
                return AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW, "文件处理失败，无法提取标书内容");
            }

            TenderReviewToolOrchestrator.OrchestrationResult orchResult =
                    tenderReviewToolOrchestrator.orchestrateWithData(context, tenderReviewData);

            if (orchResult.needsFallback()) {
                return AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW, orchResult.getErrorMessage());
            }

            return AgentExecutionResult.builder()
                    .success(true)
                    .scene(SceneEnum.TENDER_REVIEW)
                    .answer(orchResult.getPolishedAnswer())
                    .summary(orchResult.getRawResult() != null ? orchResult.getRawResult().summary() : null)
                    .riskLevel(orchResult.getRiskLevel())
                    .score(orchResult.getScore())
                    .report(orchResult.getReport())
                    .evidenceList(orchResult.getEvidenceList())
                    .steps(orchResult.getSteps())
                    .caseId(orchResult.getCaseId())
                    .documentIds(tenderReviewData.getDocuments() != null
                            ? tenderReviewData.getDocuments().stream()
                                    .map(d -> d.getDocumentId()).toList()
                            : java.util.List.of())
                    .executionTimeMs(orchResult.getExecutionTimeMs())
                    .needsFallback(false)
                    .build();

        } catch (Exception e) {
            log.error("[AgentSceneDispatcher] 带文件的标书审查失败: {}", e.getMessage(), e);
            return AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW, "标书审查执行失败: " + e.getMessage());
        }
    }

    private AgentExecutionResult dispatchToContractPrecheck(AgentChatContext context,
                                                             WorkflowRouteDecision decision) {
        log.warn("[AgentSceneDispatcher] 合同预审暂未实现，降级到通用对话");
        return dispatchToGeneralChat(context, "请进行合同条款审核");
    }

    private AgentExecutionResult dispatchToContractPrecheckWithFiles(AgentChatContext context,
                                                                      WorkflowRouteDecision decision,
                                                                      FileChatReq req) {
        log.warn("[AgentSceneDispatcher] 合同预审(带文件)暂未实现");
        return AgentExecutionResult.failure(SceneEnum.CONTRACT_PRECHECK, "合同预审功能暂未开放");
    }

    private AgentExecutionResult dispatchToRiskAlert(AgentChatContext context,
                                                     WorkflowRouteDecision decision) {
        log.warn("[AgentSceneDispatcher] 风险预警暂未实现，降级到通用对话");
        return dispatchToGeneralChat(context, "请进行风险预警分析");
    }

    public AgentExecutionResult dispatchToGeneralChat(AgentChatContext context, String query) {
        log.info("[AgentSceneDispatcher] 分发到通用对话");

        try {
            String answer = generalChatService.chat(query, context.getSessionId());

            return AgentExecutionResult.builder()
                    .success(true)
                    .scene(SceneEnum.UNKNOWN)
                    .answer(answer)
                    .summary("通用对话")
                    .riskLevel("UNKNOWN")
                    .score(0)
                    .steps(java.util.List.of("问题理解", "知识检索", "回复生成"))
                    .needsFallback(false)
                    .build();

        } catch (Exception e) {
            log.error("[AgentSceneDispatcher] 通用对话分发失败: {}", e.getMessage(), e);
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

    // ==================== 内部组件：通用对话服务 ====================

    @lombok.RequiredArgsConstructor
    public static class GeneralChatService {

        private final com.liang.drugagent.shared.llm.LlmService llmService;
        private final com.liang.drugagent.scene.common.service.ChatMemoryService chatMemoryService;

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
}
