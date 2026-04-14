package com.liang.drugagent.scene.tender_review.facade;

import com.liang.drugagent.agent.chat.AgentMessageService;
import com.liang.drugagent.agent.common.entity.ChatMessage;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.preparation.TenderReviewPreparationService;
import com.liang.drugagent.scene.tender_review.workflow.TenderReviewWorkflow;
import com.liang.drugagent.shared.llm.LlmProviderType;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmResponse;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import com.liang.drugagent.shared.model.ThinkingStep;
import com.liang.drugagent.shared.model.ThinkingStepEmitter;
import com.liang.drugagent.shared.model.ThinkingStepProgress;
import com.liang.drugagent.shared.model.WorkflowResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 标书审查场景服务 facade。
 *
 * <p>对通用层暴露的唯一入口，负责：
 * <ul>
 *   <li>调用 {@link TenderReviewPreparationService} 补齐场景数据</li>
 *   <li>校验数据是否满足最低要求</li>
 *   <li>调用 {@link TenderReviewWorkflow} 执行审查</li>
 *   <li>返回统一的 {@link AgentExecutionResult}</li>
 * </ul>
 *
 * <p>调用链（轻量化 MVP 方案）：
 * <pre>
 * AgentSceneService -> TenderReviewSceneService
 *   -> TenderReviewPreparationService
 *   -> TenderReviewWorkflow
 * </pre>
 *
 * <p>该类是标书场景在通用层唯一的感知点，
 * 通用层不应该直接依赖 Workflow。
 *
 * @author liangjiajian
 * @see TenderReviewPreparationService
 * @see TenderReviewWorkflow
 * @see AgentExecutionResult
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderReviewSceneService {

    private final TenderReviewPreparationService preparationService;
    private final TenderReviewWorkflow tenderReviewWorkflow;
    private final LlmService llmService;
    private final AgentMessageService agentMessageService;

    /**
     * 执行标书审查场景。
     *
     * <p>完整流程（轻量化 MVP 方案）：
     * <ol>
     *   <li>调用 PreparationService 准备 TenderReviewData</li>
     *   <li>校验数据是否满足最低要求（至少 2 份文档）</li>
     *   <li>调用 TenderReviewWorkflow 执行审查</li>
     *   <li>将 WorkflowResult 转换为 AgentExecutionResult</li>
     *   <li>返回统一执行结果</li>
     * </ol>
     *
     * @param context Agent 上下文
     * @param req     对话请求
     * @return 场景执行结果
     */
    public AgentExecutionResult execute(AgentChatContext context, AgentChatReq req) {
        log.info("[TenderReviewSceneService] 开始执行标书审查场景, sessionId={}, query={}",
                context.getSessionId(),
                context.getQuery() != null ? context.getQuery().substring(0, Math.min(30, context.getQuery().length())) : "null");

        try {
            // 1. 准备标书审查数据
            TenderReviewData data = preparationService.prepare(context, req);

            // 2. 校验数据是否满足最低要求
            if (!preparationService.hasEnoughDocuments(data)) {
                // 【新增】检查历史消息中是否有已完成的审查结果，支持追问模式
                AgentExecutionResult followUpResult = tryFollowUpMode(context);
                if (followUpResult != null) {
                    return followUpResult;
                }
                // 优先使用 preparationService 返回的具体错误信息
                String specificError = (String) context.getMetadata().get("preparationError");
                if (specificError != null && !specificError.isBlank()) {
                    log.warn("[TenderReviewSceneService] 标书数据准备失败: {}", specificError);
                    return buildInsufficientTenderResult(specificError);
                }
                log.warn("[TenderReviewSceneService] 标书数据不足，无法进行审查");
                return buildInsufficientTenderResult(
                        "当前可用于审查的标书文件不足。请先上传至少2份标书文件，我再继续为你审查围标风险。"
                );
            }

            // 3. 将数据设置到上下文中，供 Workflow 直接使用
            context.getMetadata().put("tenderReviewData", data);

            // 4. 调用 Workflow 执行审查
            log.info("[TenderReviewSceneService] 数据准备完成, docCount={}，开始执行 Workflow",
                    data.getDocuments().size());
            WorkflowResult workflowResult = tenderReviewWorkflow.execute(context);

            // 5. 转换为 AgentExecutionResult
            AgentExecutionResult result = AgentExecutionResult.fromWorkflowResult(workflowResult);

            // 6. 注入文件名，供前端报告抽屉展示
            if (data.getDocuments() != null && !data.getDocuments().isEmpty()) {
                List<String> docIds = new ArrayList<>();
                List<String> docNames = new ArrayList<>();
                for (var doc : data.getDocuments()) {
                    docIds.add(doc.getDocumentId());
                    String name = doc.getDocumentName() != null ? doc.getDocumentName()
                            : (doc.getFilename() != null ? doc.getFilename() : doc.getDocumentId());
                    docNames.add(name);
                }
                result.setDocumentIds(docIds);
                result.setDocumentNames(docNames);
                log.info("[TenderReviewSceneService] 注入文档信息到结果, docCount={}, names={}", docIds.size(), docNames);
            }

            log.info("[TenderReviewSceneService] 标书审查完成, success={}, riskLevel={}",
                    result.isSuccess(), result.getRiskLevel());
            return result;

        } catch (Exception e) {
            log.error("[TenderReviewSceneService] 标书审查场景执行异常: {}", e.getMessage(), e);
            return AgentExecutionResult.failure(
                    SceneEnum.TENDER_REVIEW,
                    "标书审查执行失败: " + e.getMessage()
            );
        }
    }

    /**
     * 流式执行标书审查，通过 SSE 推送每一步思考进度。
     *
     * <p>适用于前端需要实时展示思考过程的场景。
     *
     * @param context Agent 上下文
     * @param req     对话请求
     * @return 思考步骤进度流
     */
    public Flux<ThinkingStepProgress> streamExecute(AgentChatContext context, AgentChatReq req) {
        log.info("[TenderReviewSceneService] 开始流式执行标书审查, sessionId={}",
                context.getSessionId());

        // 先创建 sink，再开始准备数据，避免前端长时间停留在占位步骤。
        Sinks.Many<ThinkingStepProgress> sink = Sinks.many().unicast().onBackpressureBuffer();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                ThinkingStep routeStep = ThinkingStep.builder()
                        .code("route")
                        .title("场景识别")
                        .detail("已识别为标书审查场景，开始校验上传文件并准备审查数据")
                        .type("ROUTE")
                        .status("COMPLETED")
                        .order(1)
                        .build();
                ThinkingStep preparationStep = ThinkingStep.builder()
                        .code("prepare_data")
                        .title("数据校验与文档解析")
                        .detail("正在校验文件数量并解析标书内容")
                        .type("EXECUTION")
                        .status("PROCESSING")
                        .order(2)
                        .build();

                List<ThinkingStep> prefixSteps = new ArrayList<>();
                prefixSteps.add(routeStep);
                emitProgress(sink, ThinkingStepProgress.builder()
                        .currentCode(routeStep.getCode())
                        .currentTitle(routeStep.getTitle())
                        .currentStatus(routeStep.getStatus())
                        .currentDetail(routeStep.getDetail())
                        .currentStep(routeStep)
                        .completedSteps(List.of(routeStep))
                        .finalResult(false)
                        .build());
                emitProgress(sink, ThinkingStepProgress.builder()
                        .currentCode(preparationStep.getCode())
                        .currentTitle(preparationStep.getTitle())
                        .currentStatus(preparationStep.getStatus())
                        .currentDetail(preparationStep.getDetail())
                        .currentStep(preparationStep)
                        .completedSteps(List.of(routeStep))
                        .finalResult(false)
                        .build());

                TenderReviewData data = preparationService.prepare(context, req);
                if (!preparationService.hasEnoughDocuments(data)) {
                    String specificError = (String) context.getMetadata().get("preparationError");
                    String errorMsg = specificError != null && !specificError.isBlank()
                            ? specificError
                            : "当前可用于审查的标书文件不足。请先上传至少2份标书文件，我再继续为你审查围标风险。";
                    preparationStep.setStatus("FAILED");
                    preparationStep.setDetail(errorMsg);
                    emitProgress(sink, ThinkingStepProgress.builder()
                            .currentCode(preparationStep.getCode())
                            .currentTitle(preparationStep.getTitle())
                            .currentStatus(preparationStep.getStatus())
                            .currentDetail(preparationStep.getDetail())
                            .currentStep(preparationStep)
                            .completedSteps(List.of(routeStep))
                            .finalResult(true)
                            .build());
                    sink.tryEmitComplete();
                    return;
                }

                context.getMetadata().put("tenderReviewData", data);
                preparationStep.setStatus("COMPLETED");
                preparationStep.setDetail("已完成 " + data.getDocuments().size() + " 份标书的数据准备，开始执行审查流程");
                prefixSteps.add(preparationStep);
                emitProgress(sink, ThinkingStepProgress.builder()
                        .currentCode(preparationStep.getCode())
                        .currentTitle(preparationStep.getTitle())
                        .currentStatus(preparationStep.getStatus())
                        .currentDetail(preparationStep.getDetail())
                        .currentStep(preparationStep)
                        .completedSteps(new ArrayList<>(prefixSteps))
                        .finalResult(false)
                        .build());

                log.info("[TenderReviewSceneService] 数据准备完成, docCount={}，开始执行 Workflow",
                        data.getDocuments().size());

                tenderReviewWorkflow.executeWithProgress(context, new ThinkingStepEmitter() {
                    @Override
                    public void emit(ThinkingStepProgress progress) {
                        ThinkingStepProgress mergedProgress = mergePrefixSteps(progress, prefixSteps, context);
                        Sinks.EmitResult result = sink.tryEmitNext(mergedProgress);
                        if (result.isFailure() && !result.equals(Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER)) {
                            log.warn("[TenderReviewSceneService] 进度推送失败: {}", result);
                        }
                    }

                    @Override
                    public boolean isCancelled() {
                        return sink.currentSubscriberCount() == 0;
                    }
                });
                sink.tryEmitComplete();
            } catch (Exception e) {
                log.error("[TenderReviewSceneService] 流式执行异常: {}", e.getMessage(), e);
                sink.tryEmitError(e);
            } finally {
                executor.shutdown();
            }
        });

        // 6. 返回流
        return sink.asFlux();
    }

    private void emitProgress(Sinks.Many<ThinkingStepProgress> sink, ThinkingStepProgress progress) {
        Sinks.EmitResult result = sink.tryEmitNext(progress);
        if (result.isFailure() && !result.equals(Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER)) {
            log.warn("[TenderReviewSceneService] 进度推送失败: {}", result);
        }
    }

    private ThinkingStepProgress mergePrefixSteps(ThinkingStepProgress progress,
                                                  List<ThinkingStep> prefixSteps,
                                                  AgentChatContext context) {
        if (progress == null || prefixSteps == null || prefixSteps.isEmpty()) {
            return progress;
        }

        List<ThinkingStep> mergedCompletedSteps = new ArrayList<>(prefixSteps);
        if (progress.getCompletedSteps() != null && !progress.getCompletedSteps().isEmpty()) {
            mergedCompletedSteps.addAll(progress.getCompletedSteps());
        }

        List<String> streamFileIds = resolveStreamFileIds(context, progress.getDocumentIds());
        List<String> documentNames = resolveDocumentNames(context);

        if (progress.isFinalResult() && progress.getResult() != null) {
            List<ThinkingStep> mergedThinkingSteps = new ArrayList<>(prefixSteps);
            if (progress.getResult().getThinkingSteps() != null && !progress.getResult().getThinkingSteps().isEmpty()) {
                mergedThinkingSteps.addAll(progress.getResult().getThinkingSteps());
            }
            progress.getResult().setThinkingSteps(mergedThinkingSteps);
            progress.getResult().setTraceId(context.getTraceId());
            progress.getResult().setDocumentNames(documentNames);
            progress.getResult().setDocumentIds(streamFileIds);
        }

        return ThinkingStepProgress.builder()
                .currentCode(progress.getCurrentCode())
                .currentTitle(progress.getCurrentTitle())
                .currentStatus(progress.getCurrentStatus())
                .currentDetail(progress.getCurrentDetail())
                .currentStep(progress.getCurrentStep())
                .completedSteps(mergedCompletedSteps)
                .finalResult(progress.isFinalResult())
                .result(progress.getResult())
                .sessionTitle(progress.getSessionTitle())
                .documentIds(streamFileIds)
                .build();
    }

    private List<String> resolveDocumentNames(AgentChatContext context) {
        Object tenderReviewDataObj = context.getMetadata().get("tenderReviewData");
        if (tenderReviewDataObj instanceof TenderReviewData tenderReviewData
                && tenderReviewData.getDocuments() != null
                && !tenderReviewData.getDocuments().isEmpty()) {
            List<String> documentNames = new ArrayList<>();
            tenderReviewData.getDocuments().forEach(doc -> {
                String name = doc.getDocumentName() != null ? doc.getDocumentName()
                        : (doc.getFilename() != null ? doc.getFilename() : doc.getDocumentId());
                documentNames.add(name);
            });
            return documentNames;
        }
        return List.of();
    }

    private List<String> resolveStreamFileIds(AgentChatContext context, List<String> fallbackIds) {
        if (context.getUploadedFiles() != null && !context.getUploadedFiles().isEmpty()) {
            return context.getUploadedFiles().stream()
                    .map(file -> file.getId())
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
        }
        if (context.getFileIds() != null && !context.getFileIds().isEmpty()) {
            return new ArrayList<>(context.getFileIds());
        }
        if (fallbackIds != null && !fallbackIds.isEmpty()) {
            return new ArrayList<>(fallbackIds);
        }
        return List.of();
    }

    /**
     * 获取场景类型。
     *
     * @return TENDER_REVIEW
     */
    public SceneEnum getSceneType() {
        return SceneEnum.TENDER_REVIEW;
    }

    private AgentExecutionResult buildInsufficientTenderResult(String message) {
        return AgentExecutionResult.builder()
                .success(false)
                .scene(SceneEnum.TENDER_REVIEW)
                .errorMessage(message)
                .clarificationQuestion(message)
                .needsFallback(true)
                .build();
    }

    /**
     * 尝试追问模式：若历史消息中有已完成的审查报告，用报告内容作上下文回答用户问题。
     * 适用场景：审查完成后用户追问细节，无需重新上传文件也无需重跑 workflow。
     *
     * @return 回答结果；如果没有可用的历史报告，返回 null
     */
    private AgentExecutionResult tryFollowUpMode(AgentChatContext context) {
        // 从历史消息中找最近一条审查结果卡片
        String previousReportContent = findLatestReviewReportContent(context);
        if (previousReportContent == null) {
            return null;
        }

        log.info("[TenderReviewSceneService] 检测到历史审查结果，进入追问模式, sessionId={}", context.getSessionId());

        // 构建系统提示：以历史报告为上下文，回答用户追问
        String systemPrompt = "你是一位医药监管领域的专业AI助手，专注于围标串标风险审查。\n" +
                "用户已完成一次标书审查，以下是本次审查报告的核心内容：\n\n" +
                "【审查报告】\n" + previousReportContent + "\n\n" +
                "请基于上述审查报告回答用户的追问。\n" +
                "- 如果问题是关于报告中的某个风险点、证据或结论，直接基于报告内容解释。\n" +
                "- 如果问题超出报告范围，结合专业知识补充说明，但需明确区分。\n" +
                "- 回答应专业、简洁，避免重复审查报告的全部内容。";

        LlmRequest request = LlmRequest.builder()
                .provider(LlmProviderType.DASHSCOPE)
                .model("qwen-plus-2025-07-28")
                .sessionId(context.getSessionId())
                .systemPrompt(systemPrompt)
                .messages(List.of(LlmRequest.ChatMessage.builder()
                        .role("user")
                        .content(context.getQuery())
                        .build()))
                .build();

        try {
            LlmResponse response = llmService.chat(request);
            if (Boolean.TRUE.equals(response.getSuccess()) && response.getContent() != null) {
                return AgentExecutionResult.builder()
                        .success(true)
                        .scene(SceneEnum.TENDER_REVIEW)
                        .answer(response.getContent())
                        .summary("基于审查报告追问解答")
                        .needsFallback(false)
                        .build();
            }
        } catch (Exception e) {
            log.warn("[TenderReviewSceneService] 追问模式 LLM 调用失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从会话历史中提取最近一次成功的审查报告内容。
     */
    private String findLatestReviewReportContent(AgentChatContext context) {
        List<ChatMessage> history = context.getHistoryMessages();
        if (history == null || history.isEmpty()) {
            return null;
        }
        // 倒序查找最近一条审查结果卡片
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if ("assistant_result_card".equalsIgnoreCase(msg.getType())
                    && msg.getContent() != null && !msg.getContent().isBlank()) {
                return msg.getContent();
            }
        }
        return null;
    }
}
