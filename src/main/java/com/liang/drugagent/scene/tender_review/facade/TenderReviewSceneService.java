package com.liang.drugagent.scene.tender_review.facade;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.preparation.TenderReviewPreparationService;
import com.liang.drugagent.scene.tender_review.workflow.TenderReviewWorkflow;
import com.liang.drugagent.shared.model.AgentExecutionResult;
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

        // 1. 准备标书审查数据（同步）
        TenderReviewData data = preparationService.prepare(context, req);

        // 2. 校验数据是否满足最低要求
        if (!preparationService.hasEnoughDocuments(data)) {
            String specificError = (String) context.getMetadata().get("preparationError");
            String errorMsg = specificError != null && !specificError.isBlank()
                    ? specificError
                    : "当前可用于审查的标书文件不足。请先上传至少2份标书文件，我再继续为你审查围标风险。";
            log.warn("[TenderReviewSceneService] 标书数据不足，无法进行审查");

            // 返回错误流
            ThinkingStepProgress errorProgress = ThinkingStepProgress.builder()
                    .currentCode("error")
                    .currentTitle("数据不足")
                    .currentStatus("FAILED")
                    .currentDetail(errorMsg)
                    .finalResult(true)
                    .build();
            return Flux.just(errorProgress);
        }

        // 3. 将数据设置到上下文中
        context.getMetadata().put("tenderReviewData", data);

        log.info("[TenderReviewSceneService] 数据准备完成, docCount={}，开始执行 Workflow",
                data.getDocuments().size());

        // 4. 创建 Sinks 用于发射进度事件
        Sinks.Many<ThinkingStepProgress> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 5. 在独立线程中执行工作流，实时推送进度
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                tenderReviewWorkflow.executeWithProgress(context, new ThinkingStepEmitter() {
                    @Override
                    public void emit(ThinkingStepProgress progress) {
                        Sinks.EmitResult result = sink.tryEmitNext(progress);
                        if (result.isFailure()) {
                            log.warn("[TenderReviewSceneService] 进度推送失败: {}", result);
                        }
                    }

                    @Override
                    public boolean isCancelled() {
                        return sink.currentSubscriberCount() == 0;
                    }
                });
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
}
