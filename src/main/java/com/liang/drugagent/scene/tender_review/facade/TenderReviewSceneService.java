package com.liang.drugagent.scene.tender_review.facade;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.orchestrator.TenderReviewToolOrchestrator;
import com.liang.drugagent.scene.tender_review.preparation.TenderReviewPreparationService;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 标书审查场景服务 facade。
 *
 * <p>对通用层暴露的唯一入口，负责：
 * <ul>
 *   <li>调用 {@link TenderReviewPreparationService} 补齐场景数据</li>
 *   <li>校验数据是否满足最低要求</li>
 *   <li>调用 {@link TenderReviewToolOrchestrator} 执行审查</li>
 *   <li>返回统一的 {@link AgentExecutionResult}</li>
 * </ul>
 *
 * <p>调用链：
 * <pre>
 * AgentSceneService -> TenderReviewSceneService
 *   -> TenderReviewPreparationService
 *   -> TenderReviewToolOrchestrator
 * </pre>
 *
 * <p>该类是标书场景在通用层唯一的感知点，
 * 通用层不应该直接依赖 Orchestrator 或 Workflow。
 *
 * @author liangjiajian
 * @see TenderReviewPreparationService
 * @see TenderReviewToolOrchestrator
 * @see AgentExecutionResult
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderReviewSceneService {

    private final TenderReviewPreparationService preparationService;
    private final TenderReviewToolOrchestrator orchestrator;

    /**
     * 执行标书审查场景。
     *
     * <p>完整流程：
     * <ol>
     *   <li>调用 PreparationService 准备 TenderReviewData</li>
     *   <li>校验数据是否满足最低要求（至少 2 份文档）</li>
     *   <li>调用 Orchestrator 执行工具编排</li>
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
                log.warn("[TenderReviewSceneService] 标书数据不足，无法进行审查");
                return AgentExecutionResult.failure(
                        SceneEnum.TENDER_REVIEW,
                        "至少需要两份标书文件才能进行围标审查，请确认已上传足够的文件"
                );
            }

            // 3. 调用 Orchestrator 执行审查
            log.info("[TenderReviewSceneService] 数据准备完成, docCount={}，开始执行编排",
                    data.getDocuments().size());
            AgentExecutionResult result = orchestrator.orchestrate(data, req.getQuery(), context);

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
     * 获取场景类型。
     *
     * @return TENDER_REVIEW
     */
    public SceneEnum getSceneType() {
        return SceneEnum.TENDER_REVIEW;
    }
}
