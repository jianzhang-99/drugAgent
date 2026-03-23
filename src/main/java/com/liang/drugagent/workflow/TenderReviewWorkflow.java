package com.liang.drugagent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.context.AgentContext;
import com.liang.drugagent.domain.tenderreview.*;
import com.liang.drugagent.domain.workflow.EvidenceItem;
import com.liang.drugagent.domain.workflow.ReviewReport;
import com.liang.drugagent.domain.workflow.WorkflowResult;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.service.AgentChatService;
import com.liang.drugagent.service.tenderreview.TenderReviewDataResolver;
import com.liang.drugagent.workflow.tender.step.*;
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
