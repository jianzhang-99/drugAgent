package com.liang.drugagent.scene.tender_review.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.SceneWorkflow;
import com.liang.drugagent.agent.chat.LLMChatService;
import com.liang.drugagent.scene.tender_review.model.RiskFusionResult;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.service.EvidenceAssemblerService;
import com.liang.drugagent.scene.tender_review.service.ReportGenerationService;
import com.liang.drugagent.scene.tender_review.service.RiskFusionService;
import com.liang.drugagent.scene.tender_review.support.TenderExemptionEngine;
import com.liang.drugagent.scene.tender_review.support.TenderRuleEngine;
import com.liang.drugagent.scene.tender_review.support.assembler.TenderReviewDataAssembler;
import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.ReviewReport;
import com.liang.drugagent.shared.model.WorkflowResult;
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

    private final LLMChatService LLMChatService;
    private final TenderRuleEngine tenderRuleEngine;
    private final TenderExemptionEngine tenderExemptionEngine;
    private final RiskFusionService riskFusionService;
    private final EvidenceAssemblerService evidenceAssemblerService;
    private final ReportGenerationService reportGenerationService;
    private final ObjectMapper objectMapper;
    private final TenderReviewDataAssembler tenderReviewDataResolver;

    public TenderReviewWorkflow(LLMChatService LLMChatService,
                                TenderRuleEngine tenderRuleEngine,
                                TenderExemptionEngine tenderExemptionEngine,
                                RiskFusionService riskFusionService,
                                EvidenceAssemblerService evidenceAssemblerService,
                                ReportGenerationService reportGenerationService,
                                ObjectMapper objectMapper,
                                TenderReviewDataAssembler tenderReviewDataResolver) {
        this.LLMChatService = LLMChatService;
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
    public WorkflowResult execute(AgentChatContext context) {
        TenderReviewData tenderReviewData = readTenderReviewData(context);
        if (tenderReviewData != null) {
            return executeRuleFlow(tenderReviewData);
        }

        String answer = LLMChatService.chatWithScene(context.getQuery(), "default", context.getSessionId());
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
        // 规则执行
        List<RuleHit> allHits = tenderRuleEngine.execute(tenderReviewData);

        // 免责判定
        var exemptionResult = tenderExemptionEngine.apply(allHits, tenderReviewData);

        // 风险融合
        RiskFusionResult fusionResult = riskFusionService.fuse(
                tenderReviewData,
                exemptionResult.effectiveHits(),
                exemptionResult.exemptionHits()
        );

        // 证据组装
        var evidenceAssemblyResult = evidenceAssemblerService.assemble(
                exemptionResult.effectiveHits(),
                exemptionResult.exemptionHits(),
                fusionResult
        );

        // 报告生成
        ReviewReport report = reportGenerationService.generate(
                tenderReviewData,
                allHits,
                exemptionResult.effectiveHits(),
                exemptionResult.exemptionHits(),
                fusionResult,
                evidenceAssemblyResult
        );

        WorkflowResult result = WorkflowResult.of(
                SceneEnum.TENDER_REVIEW,
                reportGenerationService.buildAnswer(report)
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
    private TenderReviewData readTenderReviewData(AgentChatContext context) {
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
