package com.liang.drugagent.tool;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.common.log.BusinessLogger;
import com.liang.drugagent.common.log.LogConstants;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.workflow.TenderReviewWorkflow;
import com.liang.drugagent.shared.domain.model.WorkflowResult;
import com.liang.drugagent.tool.dto.ReviewTenderToolReq;
import com.liang.drugagent.tool.dto.ReviewTenderToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 标书审查工具。
 *
 * <p>作为 LLM Tool Calling 的执行载体，负责标书审查的实际执行。
 * 由 {@link com.liang.drugagent.scene.tender_review.orchestrator.TenderReviewToolOrchestrator}
 * 负责调用和管理。</p>
 *
 * <p>核心职责：
 * <ul>
 *   <li>参数校验：确保请求数据完整有效</li>
 *   <li>工作流执行：调用 TenderReviewWorkflow 完成审查</li>
 *   <li>结果转换：将 WorkflowResult 转换为统一的 ReviewTenderToolResult</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>单职责：仅负责标书审查执行，不涉及 LLM 调用和编排</li>
 *   <li>可测试：输入输出都是结构化对象，便于单元测试</li>
 *   <li>可复用：可以被 orchestrator 或其他组件独立调用</li>
 * </ul>
 *
 * @author liangjiajian
 * @see com.liang.drugagent.scene.tender_review.orchestrator.TenderReviewToolOrchestrator
 * @see TenderReviewWorkflow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewTenderTool {

    public static final String TOOL_NAME = "review_tender";
    public static final String TOOL_DESCRIPTION = """
            标书审查工具。当用户需要审查标书文件、识别围标串标风险、分析技术方案雷同等场景时调用此工具。

            输入参数：
            - sessionId: 关联会话ID
            - fileIds: 待审查文件ID列表
            - reviewFocus: 审查重点（围标风险/技术方案雷同/商务条款）
            - userInstruction: 用户额外补充说明
            - needStructuredReport: 是否需要完整报告

            返回结果包含：
            - success: 是否成功
            - caseId: 案例ID
            - summary: 摘要
            - riskLevel: 风险等级（high/medium/low）
            - score: 风险分
            - steps: 执行步骤
            - report: 结构化报告
            - evidenceList: 证据列表
            - message: 错误/补充说明
            """;

    private final TenderReviewWorkflow tenderReviewWorkflow;

    private static final BusinessLogger bizLog = BusinessLogger.forScene(LogConstants.Scene.TENDER_REVIEW);

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
     * @param request 审查请求对象
     * @return 审查结果
     */
    public ReviewTenderToolResult execute(ReviewTenderToolReq request) {
        long startTime = System.currentTimeMillis();
        bizLog.info(LogConstants.Step.EXECUTE, "开始标书审查请求");

        // 1. 参数校验
        if (request == null) {
            return ReviewTenderToolResult.failure("审查请求不能为空");
        }
        request.validate();

        try {
            // 2. 构建 AgentChatContext
            AgentChatContext context = buildContext(request);

            // 3. 调用 TenderReviewWorkflow.execute()
            bizLog.info(LogConstants.Step.COMPARE, "执行标书审查工作流");
            WorkflowResult workflowResult = tenderReviewWorkflow.execute(context);

            // 4. 转换为 ReviewTenderToolResult 返回
            long executionTimeMs = System.currentTimeMillis() - startTime;
            ReviewTenderToolResult result = mapToToolResult(workflowResult, executionTimeMs);

            bizLog.info(LogConstants.Step.REPORT,
                    "标书审查完成, riskLevel=" + result.riskLevel() + ", score=" + result.score() + ", executionTimeMs=" + executionTimeMs);

            return result;

        } catch (IllegalArgumentException e) {
            bizLog.warn(LogConstants.Step.EXECUTE, "参数校验失败: " + e.getMessage());
            return ReviewTenderToolResult.failure("参数校验失败: " + e.getMessage());
        } catch (Exception e) {
            bizLog.error(LogConstants.Step.EXECUTE, "标书审查执行失败", e);
            return ReviewTenderToolResult.failure("审查执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行标书审查（带预加载的 TenderReviewData）。
     *
     * <p>适用于 Orchestrator 已经构建好 TenderReviewData 的场景。</p>
     *
     * @param request         工具请求对象
     * @param tenderReviewData 预加载的标书审查数据
     * @return 工具执行结果
     */
    public ReviewTenderToolResult execute(ReviewTenderToolReq request, TenderReviewData tenderReviewData) {
        log.info("[ReviewTenderTool] 开始使用预加载数据执行标书审查: sessionId={}", request.sessionId());

        if (request == null) {
            return ReviewTenderToolResult.failure("请求对象不能为空");
        }

        try {
            // 构建包含 TenderReviewData 的上下文
            AgentChatContext context = buildContextWithData(request, tenderReviewData);

            // 调用工作流执行审查
            WorkflowResult workflowResult = tenderReviewWorkflow.execute(context);

            return convertToToolResult(workflowResult);

        } catch (Exception e) {
            log.error("[ReviewTenderTool] 使用预加载数据执行审查失败: {}", e.getMessage(), e);
            return ReviewTenderToolResult.failure("标书审查执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取工具名称。
     */
    public String getToolName() {
        return TOOL_NAME;
    }

    /**
     * 获取工具描述。
     */
    public String getToolDescription() {
        return TOOL_DESCRIPTION;
    }

    /**
     * 根据请求构建 Agent 上下文。
     */
    private AgentChatContext buildContext(ReviewTenderToolReq request) {
        String query = buildQueryFromRequest(request);
        return AgentChatContext.fromToolRequest(
                request.sessionId(),
                query,
                request.fileIds(),
                buildMetadata(request)
        );
    }

    /**
     * 根据请求构建包含 TenderReviewData 的 Agent 上下文。
     */
    private AgentChatContext buildContextWithData(ReviewTenderToolReq request, TenderReviewData tenderReviewData) {
        String query = buildQueryFromRequest(request);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenderReviewData", tenderReviewData);
        metadata.put("reviewFocus", request.reviewFocus());
        metadata.put("userInstruction", request.userInstruction());
        metadata.put("needStructuredReport", request.needStructuredReport());
        return AgentChatContext.fromToolRequest(
                request.sessionId(),
                query,
                request.fileIds(),
                metadata
        );
    }

    /**
     * 构建元数据。
     */
    private Map<String, Object> buildMetadata(ReviewTenderToolReq request) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("reviewFocus", request.reviewFocus());
        metadata.put("userInstruction", request.userInstruction());
        metadata.put("needStructuredReport", request.needStructuredReport());
        return metadata;
    }

    /**
     * 根据请求构建查询文本。
     */
    private String buildQueryFromRequest(ReviewTenderToolReq request) {
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
     * 将 WorkflowResult 映射为 ReviewTenderToolResult。
     */
    private ReviewTenderToolResult mapToToolResult(WorkflowResult workflowResult, long executionTimeMs) {
        if (workflowResult == null) {
            return ReviewTenderToolResult.failure("工作流执行结果为空");
        }

        String caseId = extractCaseId(workflowResult);
        if (caseId == null) {
            caseId = generateCaseId();
        }

        String summary = buildSummary(workflowResult);
        List<String> steps = workflowResult.getSteps() != null
                ? workflowResult.getSteps()
                : List.of("参数校验", "上下文构建", "工作流执行", "结果转换");

        return ReviewTenderToolResult.success(
                caseId,
                summary,
                workflowResult.getRiskLevel(),
                workflowResult.getScore(),
                steps,
                workflowResult.getReport(),
                workflowResult.getEvidenceList()
        );
    }

    /**
     * 生成案例ID。
     */
    private String generateCaseId() {
        return "CASE-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
