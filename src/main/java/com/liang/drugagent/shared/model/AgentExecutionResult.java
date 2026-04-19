package com.liang.drugagent.shared.model;

import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.model.report.ReportData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行结果。
 *
 * <p>用于在 AgentChatService 编排层与下游执行器之间传递统一执行结果。
 * 包含执行状态、输出数据、错误信息等。</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionResult {

    /**
     * 执行状态。
     */
    private boolean success;

    /**
     * 执行场景。
     */
    private SceneEnum scene;

    /**
     * AI 回答内容。
     */
    private String answer;

    /**
     * AI 思考内容。
     */
    private String reasoningContent;

    /**
     * 摘要信息。
     */
    private String summary;

    /**
     * 风险等级。
     */
    private String riskLevel;

    /**
     * 综合评分。
     */
    private Integer score;

    /**
     * 关联案例ID。
     */
    private String caseId;

    /**
     * 文档ID列表。
     */
    private List<String> documentIds = new ArrayList<>();

    /**
     * 文档名称列表（与 documentIds 对应，供前端展示文件名）。
     */
    private List<String> documentNames = new ArrayList<>();

    /**
     * 审查报告。
     */
    private ReviewReport report;

    /**
     * 结构化报告数据（报告决策页面使用）。
     */
    private ReportData reportData;

    /**
     * 证据列表。
     */
    private List<EvidenceItem> evidenceList = new ArrayList<>();

    /**
     * 证据分组列表。
     */
    private List<EvidenceGroup> evidenceGroups = new ArrayList<>();

    /**
     * 执行步骤列表。
     */
    private List<String> steps = new ArrayList<>();

    /**
     * 前端可展示的思考过程步骤。
     */
    private List<ThinkingStep> thinkingSteps = new ArrayList<>();

    /**
     * 错误信息（如果执行失败）。
     */
    private String errorMessage;

    /**
     * 是否需要降级处理。
     */
    private boolean needsFallback;

    /**
     * 执行耗时（毫秒）。
     */
    private Long executionTimeMs;

    /**
     * 是否需要更新会话标题。
     */
    private boolean shouldUpdateTitle;

    /**
     * LLM 生成的会话标题。
     */
    private String generatedTitle;

    /**
     * 澄清问题（当 needsClarification 为 true 时）。
     */
    private String clarificationQuestion;

    /**
     * 创建成功结果。
     */
    public static AgentExecutionResult success(SceneEnum scene, String answer) {
        AgentExecutionResult result = new AgentExecutionResult();
        result.setSuccess(true);
        result.setScene(scene);
        result.setAnswer(answer);
        result.setNeedsFallback(false);
        return result;
    }

    /**
     * 创建失败结果。
     */
    public static AgentExecutionResult failure(SceneEnum scene, String errorMessage) {
        AgentExecutionResult result = new AgentExecutionResult();
        result.setSuccess(false);
        result.setScene(scene);
        result.setErrorMessage(errorMessage);
        result.setNeedsFallback(true);
        return result;
    }

    /**
     * 从 WorkflowResult 转换。
     */
    public static AgentExecutionResult fromWorkflowResult(WorkflowResult workflowResult) {
        if (workflowResult == null) {
            return failure(null, "工作流执行结果为空");
        }
        AgentExecutionResult result = new AgentExecutionResult();
        result.setSuccess(true);
        result.setScene(workflowResult.getScene());
        result.setAnswer(workflowResult.getAnswer());
        result.setSummary(workflowResult.getSummary());
        result.setRiskLevel(workflowResult.getRiskLevel());
        result.setScore(workflowResult.getScore());
        result.setReport(workflowResult.getReport());
        result.setReportData(workflowResult.getReportData());
        result.setEvidenceList(workflowResult.getEvidenceList());
        result.setEvidenceGroups(workflowResult.getEvidenceGroups());
        result.setSteps(workflowResult.getSteps());
        result.setThinkingSteps(workflowResult.getThinkingSteps());
        if (workflowResult.getReport() != null) {
            result.setCaseId(workflowResult.getReport().getCaseId());
        }
        result.setNeedsFallback(false);
        return result;
    }
}
