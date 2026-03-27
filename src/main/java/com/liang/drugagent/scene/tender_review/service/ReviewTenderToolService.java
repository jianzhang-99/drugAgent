package com.liang.drugagent.scene.tender_review.service;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.tool.ReviewTenderToolRequest;
import com.liang.drugagent.scene.tender_review.tool.ReviewTenderToolResult;
import com.liang.drugagent.scene.tender_review.workflow.TenderReviewWorkflow;
import com.liang.drugagent.shared.model.WorkflowResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 标书审查工具服务。
 *
 * <p>作为 Tool 执行入口，负责承接 LLM 的工具调用请求。
 * 核心职责：
 * <ul>
 *   <li>参数校验</li>
 *   <li>整理工具请求对象</li>
 *   <li>调用 TenderReviewWorkflow</li>
 *   <li>返回结构化 ReviewTenderToolResult</li>
 * </ul>
 *
 * <p>该 Service 与 ReviewTenderTool（LLM tool calling 入口）的区别：
 * <ul>
 *   <li>ReviewTenderTool：LLM 调用时的工具接口定义</li>
 *   <li>ReviewTenderToolService：纯业务逻辑执行层，直接调用 Workflow</li>
 * </ul>
 *
 * <p>调用链路：
 * <pre>
 * TenderReviewToolOrchestrator -> ReviewTenderToolService -> TenderReviewWorkflow
 * </pre>
 *
 * @author liangjiajian
 * @see ReviewTenderToolRequest
 * @see ReviewTenderToolResult
 * @see TenderReviewWorkflow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewTenderToolService {

    private final TenderReviewWorkflow tenderReviewWorkflow;

    /**
     * 执行标书审查。
     *
     * <p>完整流程：
     * <ol>
     *   <li>参数校验</li>
     *   <li>构建 AgentChatContext</li>
     *   <li>调用 TenderReviewWorkflow.execute()</li>
     *   <li>转换为 ReviewTenderToolResult 返回</li>
     * </ol>
     *
     * @param request         工具请求对象
     * @param tenderReviewData 预加载的标书审查数据
     * @return 工具执行结果
     */
    public ReviewTenderToolResult execute(ReviewTenderToolRequest request, TenderReviewData tenderReviewData) {
        log.info("[ReviewTenderToolService] 开始执行标书审查, sessionId={}", request.sessionId());

        // 1. 参数校验
        validateRequest(request);

        try {
            // 2. 构建包含 TenderReviewData 的上下文
            AgentChatContext context = buildContextWithData(request, tenderReviewData);

            // 3. 调用 TenderReviewWorkflow 执行审查
            log.info("[ReviewTenderToolService] 调用标书审查工作流");
            WorkflowResult workflowResult = tenderReviewWorkflow.execute(context);

            // 4. 将 WorkflowResult 转换为 ReviewTenderToolResult
            return convertToToolResult(workflowResult);

        } catch (IllegalArgumentException e) {
            log.warn("[ReviewTenderToolService] 参数校验失败: {}", e.getMessage());
            return ReviewTenderToolResult.failure("参数校验失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[ReviewTenderToolService] 标书审查执行失败: {}", e.getMessage(), e);
            return ReviewTenderToolResult.failure("标书审查执行失败: " + e.getMessage());
        }
    }

    /**
     * 校验请求参数。
     *
     * @throws IllegalArgumentException 校验失败时抛出
     */
    private void validateRequest(ReviewTenderToolRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求对象不能为空");
        }
        request.validate();
    }

    /**
     * 根据请求构建包含 TenderReviewData 的 Agent 上下文。
     */
    private AgentChatContext buildContextWithData(ReviewTenderToolRequest request, TenderReviewData tenderReviewData) {
        String query = buildQueryFromRequest(request);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenderReviewData", tenderReviewData);
        metadata.put("reviewFocus", request.reviewFocus());
        metadata.put("userInstruction", request.userInstruction());
        metadata.put("needStructuredReport", request.needStructuredReport());
        return AgentChatContext.fromToolRequest(
                request.sessionId(),
                query,
                null,
                request.fileIds(),
                metadata
        );
    }

    /**
     * 根据请求构建查询文本。
     */
    private String buildQueryFromRequest(ReviewTenderToolRequest request) {
        StringBuilder query = new StringBuilder();
        query.append("标书审查请求");

        if (request.reviewFocus() != null && !request.reviewFocus().isBlank()) {
            query.append("，审查重点：").append(request.reviewFocus());
        }

        if (request.userInstruction() != null && !request.userInstruction().isBlank()) {
            query.append("，用户补充：").append(request.userInstruction());
        }

        if (Boolean.TRUE.equals(request.needStructuredReport())) {
            query.append("，需要完整报告");
        }

        return query.toString();
    }

    /**
     * 将 WorkflowResult 转换为 ReviewTenderToolResult。
     */
    private ReviewTenderToolResult convertToToolResult(WorkflowResult workflowResult) {
        if (workflowResult == null) {
            return ReviewTenderToolResult.failure("工作流执行返回为空");
        }

        String caseId = extractCaseId(workflowResult);
        String summary = buildSummary(workflowResult);

        return ReviewTenderToolResult.success(
                caseId,
                summary,
                workflowResult.getRiskLevel(),
                workflowResult.getScore(),
                workflowResult.getSteps(),
                workflowResult.getReport(),
                workflowResult.getEvidenceList()
        );
    }

    /**
     * 提取案例ID。
     */
    private String extractCaseId(WorkflowResult workflowResult) {
        if (workflowResult.getReport() != null) {
            return workflowResult.getReport().getCaseId();
        }
        return null;
    }

    /**
     * 构建摘要信息。
     */
    private String buildSummary(WorkflowResult result) {
        if (result.getReport() != null
                && result.getReport().getOverview() != null
                && result.getReport().getOverview().getSummary() != null) {
            return result.getReport().getOverview().getSummary();
        }
        return result.getAnswer();
    }

    /**
     * 生成案例ID。
     */
    private String generateCaseId() {
        return "CASE-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
