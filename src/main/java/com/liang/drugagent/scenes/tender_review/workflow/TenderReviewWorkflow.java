package com.liang.drugagent.scenes.tender_review.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.core.agent.AgentContext;
import com.liang.drugagent.core.agent.SceneWorkflow;
import com.liang.drugagent.scenes.tender_review.domain.model.*;
import com.liang.drugagent.core.domain.model.EvidenceItem;
import com.liang.drugagent.core.domain.model.ReviewReport;
import com.liang.drugagent.core.domain.model.WorkflowResult;
import com.liang.drugagent.core.agent.SceneEnum;
import com.liang.drugagent.scenes.general_qa.application.services.AgentChatService;
import com.liang.drugagent.scenes.tender_review.infrastructure.parser.TenderReviewDataResolver;
import com.liang.drugagent.core.orchestration.executor.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 标书审查工作流。
 * 负责编排标书审查的完整链路，包括数据解析、规则执行、免责处理、风险融合及证据组装。
 *
 * @author liangjiajian
 */
@Component
public class TenderReviewWorkflow implements SceneWorkflow {

    private final AgentChatService agentChatService;
    private final RuleExecutionStep ruleExecutionStep;
    private final ExemptionStep exemptionStep;
    private final RiskFusionStep riskFusionStep;
    private final EvidenceAssemblyStep evidenceAssemblyStep;
    private final ReportGenerationStep reportGenerationStep;
    private final ObjectMapper objectMapper;
    private final TenderReviewDataResolver tenderReviewDataResolver;

    public TenderReviewWorkflow(AgentChatService agentChatService,
                                RuleExecutionStep ruleExecutionStep,
                                ExemptionStep exemptionStep,
                                RiskFusionStep riskFusionStep,
                                EvidenceAssemblyStep evidenceAssemblyStep,
                                ReportGenerationStep reportGenerationStep,
                                ObjectMapper objectMapper,
                                TenderReviewDataResolver tenderReviewDataResolver) {
        this.agentChatService = agentChatService;
        this.ruleExecutionStep = ruleExecutionStep;
        this.exemptionStep = exemptionStep;
        this.riskFusionStep = riskFusionStep;
        this.evidenceAssemblyStep = evidenceAssemblyStep;
        this.reportGenerationStep = reportGenerationStep;
        this.objectMapper = objectMapper;
        this.tenderReviewDataResolver = tenderReviewDataResolver;
    }

    @Override
    public SceneEnum support() {
        return SceneEnum.TENDER_REVIEW;
    }

    /**
     * 执行标书审查工作流。
     *
     * <p>完整流程包括：
     * <ol>
     *   <li>从上下文中读取标书审查数据</li>
     *   <li>执行规则命中分析</li>
     *   <li>应用免责判定</li>
     *   <li>风险融合计算</li>
     *   <li>证据组装</li>
     *   <li>生成审查报告</li>
     * </ol>
     *
     * <p>如果上下文中没有标书数据，则降级为通用对话能力。</p>
     *
     * @param context Agent 上下文
     * @return 工作流执行结果
     */
    @Override
    public WorkflowResult execute(AgentContext context) {
        TenderReviewData tenderReviewData = readTenderReviewData(context);
        if (tenderReviewData != null) {
            return executeRuleFlow(tenderReviewData);
        }

        String answer = agentChatService.chatWithScene(context.getQuery(), "default", context.getSessionId());
        WorkflowResult result = WorkflowResult.of(SceneEnum.TENDER_REVIEW, answer);
        result.setRiskLevel("NONE");
        result.setScore(0);
        result.setSteps(List.of("场景路由", "通用审查"));
        result.setEvidenceList(List.of(
                new EvidenceItem("system_note", "MVP fallback path is still using generic chat ability.", "system")
        ));
        return result;
    }

    /**
     * 执行完整规则审查流程。
     *
     * @param tenderReviewData 标书审查数据
     * @return 包含报告和证据的工作流结果
     */
    private WorkflowResult executeRuleFlow(TenderReviewData tenderReviewData) {
        RuleResult ruleResult = ruleExecutionStep.execute(tenderReviewData);
        ExemptionResult exemptionResult = exemptionStep.apply(ruleResult.getHits(), tenderReviewData);
        List<RuleHit> effectiveHits = exemptionResult.getEffectiveHits();
        RiskFusionResult fusionResult = riskFusionStep.fuse(
                tenderReviewData,
                effectiveHits,
                exemptionResult.getExemptionHits()
        );
        var evidenceAssemblyResult = evidenceAssemblyStep.assemble(
                effectiveHits,
                exemptionResult.getExemptionHits(),
                fusionResult
        );
        ReviewReport report = reportGenerationStep.generate(
                tenderReviewData,
                ruleResult.getHits(),
                effectiveHits,
                exemptionResult.getExemptionHits(),
                fusionResult,
                evidenceAssemblyResult
        );

        WorkflowResult result = WorkflowResult.of(
                SceneEnum.TENDER_REVIEW,
                reportGenerationStep.buildAnswer(report)
        );
        result.setRiskLevel(fusionResult.getRiskLevel());
        result.setScore(fusionResult.getScore() != null ? fusionResult.getScore() : 0);
        result.setSteps(List.of("场景路由", "结构化加载", "规则命中分析", "误报豁免", "风险融合", "证据组装", "报告生成"));
        result.setReport(report);
        result.setEvidenceList(evidenceAssemblyResult.getFlatItems());
        result.setEvidenceGroups(evidenceAssemblyResult.getGroups());
        return result;
    }

    /**
     * 从 Agent 上下文中读取标书审查数据。
     *
     * <p>优先使用 TenderReviewDataResolver 进行解析，
     * 如果解析失败则尝试从 metadata 中直接获取。</p>
     *
     * @param context Agent 上下文
     * @return 标书审查数据（如果能获取到）
     */
    private TenderReviewData readTenderReviewData(AgentContext context) {
        TenderReviewData resolved = tenderReviewDataResolver.resolve(context);
        if (resolved != null) {
            return resolved;
        }
        Map<String, Object> metadata = context == null ? null : context.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object rawData = metadata.get("tenderReviewData");
        if (rawData == null) {
            return null;
        }
        return objectMapper.convertValue(rawData, TenderReviewData.class);
    }

}
