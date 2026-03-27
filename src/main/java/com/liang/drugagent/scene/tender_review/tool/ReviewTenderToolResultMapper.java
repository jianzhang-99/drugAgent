package com.liang.drugagent.scene.tender_review.tool;

import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.ReviewReport;
import com.liang.drugagent.shared.model.WorkflowResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 标书审查工具结果映射器。
 *
 * <p>负责在不同结果对象之间进行转换：</p>
 * <ul>
 *   <li>WorkflowResult -> ReviewTenderToolResult</li>
 *   <li>ReviewTenderToolResult -> AgentChatResp</li>
 * </ul>
 *
 * <p>该映射器确保数据结构在不同层级之间传递时的一致性。</p>
 *
 * @author liangjiajian
 */
@Slf4j
@Component
public class ReviewTenderToolResultMapper {

    /**
     * 将 WorkflowResult 转换为 ReviewTenderToolResult。
     *
     * @param workflowResult 工作流执行结果
     * @return 标书审查工具结果
     */
    public ReviewTenderToolResult toToolResult(WorkflowResult workflowResult) {
        if (workflowResult == null) {
            return ReviewTenderToolResult.failure("工作流执行结果为空");
        }

        String caseId = extractCaseId(workflowResult);
        String summary = extractSummary(workflowResult);

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
     * 将 ReviewTenderToolResult 转换为 AgentChatResp。
     *
     * @param toolResult 工具执行结果
     * @param context Agent 上下文信息
     * @param traceId 追踪ID
     * @param routeReason 路由原因
     * @return AgentChatResp 响应对象
     */
    public AgentChatResp toDrugAgentResp(ReviewTenderToolResult toolResult,
                                         String context,
                                         String traceId,
                                         String routeReason) {
        AgentChatResp resp = new AgentChatResp();
        resp.setTraceId(traceId);
        resp.setScene(SceneEnum.TENDER_REVIEW.name());
        resp.setRouteReason(routeReason);
        resp.setRouteSource("tender_review_tool");
        resp.setConfidence(1.0);

        if (toolResult == null) {
            resp.setAnswer("审查结果为空");
            resp.setSummary("标书审查");
            resp.setRiskLevel("UNKNOWN");
            resp.setScore(0);
            return resp;
        }

        if (toolResult.success()) {
            resp.setSummary(toolResult.summary());
            resp.setAnswer(buildAnswer(toolResult));
            resp.setRiskLevel(toolResult.riskLevel());
            resp.setScore(toolResult.score() != null ? toolResult.score() : 0);
            resp.setSteps(toolResult.steps() != null ? toolResult.steps() : List.of());
            resp.setReport(toolResult.report());
            resp.setEvidenceList(toolResult.evidenceList());

            // 设置案例ID
            if (toolResult.caseId() != null) {
                resp.setCaseId(toolResult.caseId());
            }
        } else {
            resp.setAnswer("标书审查失败: " + toolResult.message());
            resp.setSummary("标书审查失败");
            resp.setRiskLevel("UNKNOWN");
            resp.setScore(0);
        }

        return resp;
    }

    /**
     * 从 WorkflowResult 中提取案例ID。
     */
    private String extractCaseId(WorkflowResult workflowResult) {
        if (workflowResult.getReport() != null) {
            return workflowResult.getReport().getCaseId();
        }
        return null;
    }

    /**
     * 从 WorkflowResult 中提取摘要。
     */
    private String extractSummary(WorkflowResult workflowResult) {
        if (workflowResult.getReport() != null
                && workflowResult.getReport().getOverview() != null
                && workflowResult.getReport().getOverview().getSummary() != null) {
            return workflowResult.getReport().getOverview().getSummary();
        }
        return workflowResult.getAnswer();
    }

    /**
     * 构建用户友好的回答文本。
     */
    private String buildAnswer(ReviewTenderToolResult toolResult) {
        if (toolResult == null) {
            return "审查完成，但未获取到有效结果。";
        }

        StringBuilder sb = new StringBuilder();

        // 风险等级和分数
        if (toolResult.riskLevel() != null) {
            sb.append("## 审查结果\n\n");
            sb.append("**风险等级**: ").append(formatRiskLevel(toolResult.riskLevel())).append("\n");
            if (toolResult.score() != null) {
                sb.append("**风险分数**: ").append(toolResult.score()).append("\n");
            }
            sb.append("\n");
        }

        // 摘要
        if (toolResult.summary() != null) {
            sb.append("## 摘要\n\n");
            sb.append(toolResult.summary()).append("\n\n");
        }

        // 风险项
        if (toolResult.report() != null && toolResult.report().getRiskItems() != null
                && !toolResult.report().getRiskItems().isEmpty()) {
            sb.append("## 主要风险项\n\n");
            for (ReviewReport.RiskItem riskItem : toolResult.report().getRiskItems()) {
                sb.append("### ").append(riskItem.getTitle()).append("\n");
                sb.append("- 风险类型: ").append(riskItem.getRiskType()).append("\n");
                sb.append("- 风险等级: ").append(formatRiskLevel(riskItem.getRiskLevel())).append("\n");
                if (riskItem.getSummary() != null) {
                    sb.append("- 说明: ").append(riskItem.getSummary()).append("\n");
                }
                if (riskItem.getRecommendations() != null && !riskItem.getRecommendations().isEmpty()) {
                    sb.append("- 建议: ").append(String.join("; ", riskItem.getRecommendations())).append("\n");
                }
                sb.append("\n");
            }
        }

        // 证据列表
        if (toolResult.evidenceList() != null && !toolResult.evidenceList().isEmpty()) {
            sb.append("## 证据详情\n\n");
            for (int i = 0; i < toolResult.evidenceList().size(); i++) {
                EvidenceItem evidence = toolResult.evidenceList().get(i);
                sb.append("**").append(i + 1).append(". ").append(evidence.getTitle()).append("**\n");
                if (evidence.getContent() != null) {
                    sb.append(evidence.getContent()).append("\n");
                }
                if (evidence.getSource() != null) {
                    sb.append("来源: ").append(evidence.getSource()).append("\n");
                }
                sb.append("\n");
            }
        }

        // 补充说明
        if (toolResult.message() != null && !toolResult.message().isBlank()) {
            sb.append("## 补充说明\n\n");
            sb.append(toolResult.message()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 格式化风险等级显示。
     */
    private String formatRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return "未知";
        }
        return switch (riskLevel.toUpperCase()) {
            case "HIGH" -> "高风险";
            case "MEDIUM" -> "中风险";
            case "LOW" -> "低风险";
            case "NONE" -> "无风险";
            default -> riskLevel;
        };
    }
}
