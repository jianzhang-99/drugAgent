package com.liang.drugagent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.tenderreview.ExemptionResult;
import com.liang.drugagent.domain.tenderreview.RiskFusionResult;
import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.domain.workflow.EvidenceAssemblyResult;
import com.liang.drugagent.domain.workflow.EvidenceItem;
import com.liang.drugagent.domain.workflow.ReviewReport;
import com.liang.drugagent.domain.workflow.WorkflowResult;
import com.liang.drugagent.engine.TenderExemptionEngine;
import com.liang.drugagent.engine.TenderRuleEngine;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.service.AgentChatService;
import com.liang.drugagent.service.tenderreview.EvidenceAssemblerService;
import com.liang.drugagent.service.tenderreview.ReportGenerationService;
import com.liang.drugagent.service.tenderreview.RiskFusionService;
import com.liang.drugagent.service.tenderreview.TenderReviewDataResolver;
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
    private final TenderRuleEngine tenderRuleEngine;
    private final TenderExemptionEngine tenderExemptionEngine;
    private final RiskFusionService riskFusionService;
    private final EvidenceAssemblerService evidenceAssemblerService;
    private final ReportGenerationService reportGenerationService;
    private final ObjectMapper objectMapper;
    private final TenderReviewDataResolver tenderReviewDataResolver;

    public TenderReviewWorkflow(AgentChatService agentChatService,
                                TenderRuleEngine tenderRuleEngine,
                                TenderExemptionEngine tenderExemptionEngine,
                                RiskFusionService riskFusionService,
                                EvidenceAssemblerService evidenceAssemblerService,
                                ReportGenerationService reportGenerationService,
                                ObjectMapper objectMapper,
                                TenderReviewDataResolver tenderReviewDataResolver) {
        this.agentChatService = agentChatService;
        this.tenderRuleEngine = tenderRuleEngine;
        this.tenderExemptionEngine = tenderExemptionEngine;
        this.riskFusionService = riskFusionService;
        this.evidenceAssemblerService = evidenceAssemblerService;
        this.reportGenerationService = reportGenerationService;
        this.objectMapper = objectMapper;
        this.tenderReviewDataResolver = tenderReviewDataResolver;
    }

    @Override
    public SceneEnum support() {
        return SceneEnum.TENDER_REVIEW;
    }

    /**
     * 执行标书审查工作流。
     * 如果上下文中存在标书审查数据，则执行结构化的规则流；
     * 否则，回退到通用的场景对话模式。
     *
     * @param context 执行上下文
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
        result.setSteps(List.of("scene_route", "generic_review"));
        result.setEvidenceList(List.of(
                new EvidenceItem("system_note", "MVP fallback path is still using generic chat ability.", "system")
        ));
        return result;
    }

    /**
     * 执行结构化的标书审查规则流。
     * 包括规则引擎执行、免责逻辑触发、风险分值融合以及证据组装。
     *
     * @param tenderReviewData 标书审查数据
     * @return 组装后的工作流结果
     */
    private WorkflowResult executeRuleFlow(TenderReviewData tenderReviewData) {
        RuleResult ruleResult = tenderRuleEngine.execute(tenderReviewData);
        ExemptionResult exemptionResult = tenderExemptionEngine.apply(ruleResult.getHits(), tenderReviewData);
        List<RuleHit> effectiveHits = exemptionResult.getEffectiveHits();
        RiskFusionResult fusionResult = riskFusionService.fuse(
                tenderReviewData,
                effectiveHits,
                exemptionResult.getExemptionHits()
        );
        EvidenceAssemblyResult evidenceAssemblyResult = evidenceAssemblerService.assemble(
                effectiveHits,
                exemptionResult.getExemptionHits(),
                fusionResult
        );
        ReviewReport report = reportGenerationService.generate(
                tenderReviewData,
                ruleResult.getHits(),
                effectiveHits,
                exemptionResult.getExemptionHits(),
                fusionResult,
                evidenceAssemblyResult
        );

        WorkflowResult result = WorkflowResult.of(
                SceneEnum.TENDER_REVIEW,
                reportGenerationService.buildAnswer(report)
        );
        result.setRiskLevel(fusionResult.getRiskLevel());
        result.setSteps(List.of("scene_route", "structured_load", "rule_hit", "false_positive_exemption", "risk_fusion", "evidence_assembly", "report_generation"));
        result.setReport(report);
        result.setEvidenceList(evidenceAssemblyResult.getFlatItems());
        result.setEvidenceGroups(evidenceAssemblyResult.getGroups());
        return result;
    }

    /**
     * 从上下文中提取并解析标书审查结构化数据。
     *
     * @param context 执行上下文
     * @return 解析后的标书审查数据，若无则返回 null
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
