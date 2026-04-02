package com.liang.drugagent.scene.tender_review.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.agent.chat.LLMChatService;
import com.liang.drugagent.scene.tender_review.model.RiskFusionResult;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.CommercialCoordinationSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.ImplementationMethodSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.ProposalSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.RiskIdentificationSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.ServiceCommitmentSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.TeamOverlapSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.service.EvidenceAssemblerService;
import com.liang.drugagent.scene.tender_review.service.ReportGenerationService;
import com.liang.drugagent.scene.tender_review.service.RiskFusionService;
import com.liang.drugagent.scene.tender_review.support.TenderExemptionEngine;
import com.liang.drugagent.scene.tender_review.support.TenderRuleEngine;
import com.liang.drugagent.scene.tender_review.support.assembler.TenderReviewDataAssembler;
import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.ReviewReport;
import com.liang.drugagent.shared.model.WorkflowResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 标书审查工作流。
 * 负责编排标书审查的完整链路，包括数据解析、确定性规则执行、LLM语义补强、免责处理、风险融合及证据组装。
 *
 * @author liangjiajian
 */
@Slf4j
@Component
public class TenderReviewWorkflow {

    private final LLMChatService LLMChatService;
    private final TenderRuleEngine tenderRuleEngine;
    private final TenderExemptionEngine tenderExemptionEngine;
    private final RiskFusionService riskFusionService;
    private final EvidenceAssemblerService evidenceAssemblerService;
    private final ReportGenerationService reportGenerationService;
    private final ObjectMapper objectMapper;
    private final TenderReviewDataAssembler tenderReviewDataResolver;
    private final ProposalSemanticAnalyzer proposalSemanticAnalyzer;
    private final RiskIdentificationSemanticAnalyzer riskIdentificationSemanticAnalyzer;
    private final CommercialCoordinationSemanticAnalyzer commercialCoordinationSemanticAnalyzer;
    private final ImplementationMethodSemanticAnalyzer implementationMethodSemanticAnalyzer;
    private final ServiceCommitmentSemanticAnalyzer serviceCommitmentSemanticAnalyzer;
    private final TeamOverlapSemanticAnalyzer teamOverlapSemanticAnalyzer;

    public TenderReviewWorkflow(LLMChatService LLMChatService,
                                TenderRuleEngine tenderRuleEngine,
                                TenderExemptionEngine tenderExemptionEngine,
                                RiskFusionService riskFusionService,
                                EvidenceAssemblerService evidenceAssemblerService,
                                ReportGenerationService reportGenerationService,
                                ObjectMapper objectMapper,
                                TenderReviewDataAssembler tenderReviewDataResolver,
                                ProposalSemanticAnalyzer proposalSemanticAnalyzer,
                                RiskIdentificationSemanticAnalyzer riskIdentificationSemanticAnalyzer,
                                CommercialCoordinationSemanticAnalyzer commercialCoordinationSemanticAnalyzer,
                                ImplementationMethodSemanticAnalyzer implementationMethodSemanticAnalyzer,
                                ServiceCommitmentSemanticAnalyzer serviceCommitmentSemanticAnalyzer,
                                TeamOverlapSemanticAnalyzer teamOverlapSemanticAnalyzer) {
        this.LLMChatService = LLMChatService;
        this.tenderRuleEngine = tenderRuleEngine;
        this.tenderExemptionEngine = tenderExemptionEngine;
        this.riskFusionService = riskFusionService;
        this.evidenceAssemblerService = evidenceAssemblerService;
        this.reportGenerationService = reportGenerationService;
        this.objectMapper = objectMapper;
        this.tenderReviewDataResolver = tenderReviewDataResolver;
        this.proposalSemanticAnalyzer = proposalSemanticAnalyzer;
        this.riskIdentificationSemanticAnalyzer = riskIdentificationSemanticAnalyzer;
        this.commercialCoordinationSemanticAnalyzer = commercialCoordinationSemanticAnalyzer;
        this.implementationMethodSemanticAnalyzer = implementationMethodSemanticAnalyzer;
        this.serviceCommitmentSemanticAnalyzer = serviceCommitmentSemanticAnalyzer;
        this.teamOverlapSemanticAnalyzer = teamOverlapSemanticAnalyzer;
    }

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
    public WorkflowResult execute(AgentChatContext context) {
        TenderReviewData tenderReviewData = readTenderReviewData(context);
        if (tenderReviewData != null) {
            return executeRuleFlow(tenderReviewData);
        }

        String answer = LLMChatService.chatWithScene(context.getQuery(), SceneEnum.DEFAULT, context.getSessionId(), context.getModel());
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
        // 确定性规则执行
        List<RuleHit> allHits = tenderRuleEngine.execute(tenderReviewData);

        // LLM 语义命中补强（W-P1/W-P4/W-M8/W-P2/W-P3/W-M3）
        List<RuleHit> semanticHits = executeSemanticAnalyzers(tenderReviewData);
        allHits.addAll(semanticHits);

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
        result.setSteps(List.of("场景路由", "结构化加载", "规则命中分析", "LLM语义分析", "误报豁免", "风险融合", "证据组装", "报告生成"));
        result.setReport(report);
        result.setEvidenceList(evidenceAssemblyResult.getFlatItems());
        result.setEvidenceGroups(evidenceAssemblyResult.getGroups());
        return result;
    }

    /**
     * 执行 LLM 语义分析器集合。
     * 对 W-P1、W-P4、W-M8（W-P2、W-P3、W-M3）规则进行 LLM 语义补强判断。
     *
     * @param tenderReviewData 标书审查数据
     * @return LLM 语义命中的规则列表
     */
    private List<RuleHit> executeSemanticAnalyzers(TenderReviewData tenderReviewData) {
        List<RuleHit> allSemanticHits = new ArrayList<>();
        String caseId = tenderReviewData.getACase() != null ? tenderReviewData.getACase().getCaseId() : "unknown";
        log.info("[TenderReviewWorkflow] 开始执行 LLM 语义分析（W-P1/W-P4/W-M8/W-P2/W-P3/W-M3） - caseId: {}", caseId);

        // Phase 1 分析器
        try {
            // W-P1 技术方案抄袭语义分析
            List<RuleHit> wp1Hits = proposalSemanticAnalyzer.analyze(tenderReviewData);
            allSemanticHits.addAll(wp1Hits);
            log.info("[TenderReviewWorkflow] W-P1 语义分析完成，命中数: {} - caseId: {}", wp1Hits.size(), caseId);
        } catch (Exception e) {
            log.error("[TenderReviewWorkflow] W-P1 语义分析异常 - caseId: {}, error: {}", caseId, e.getMessage());
        }

        try {
            // W-P4 风险识别抄袭语义分析
            List<RuleHit> wp4Hits = riskIdentificationSemanticAnalyzer.analyze(tenderReviewData);
            allSemanticHits.addAll(wp4Hits);
            log.info("[TenderReviewWorkflow] W-P4 语义分析完成，命中数: {} - caseId: {}", wp4Hits.size(), caseId);
        } catch (Exception e) {
            log.error("[TenderReviewWorkflow] W-P4 语义分析异常 - caseId: {}, error: {}", caseId, e.getMessage());
        }

        try {
            // W-M8 商务条款配合语义分析
            List<RuleHit> wm8Hits = commercialCoordinationSemanticAnalyzer.analyze(tenderReviewData);
            allSemanticHits.addAll(wm8Hits);
            log.info("[TenderReviewWorkflow] W-M8 语义分析完成，命中数: {} - caseId: {}", wm8Hits.size(), caseId);
        } catch (Exception e) {
            log.error("[TenderReviewWorkflow] W-M8 语义分析异常 - caseId: {}, error: {}", caseId, e.getMessage());
        }

        // Phase 2 分析器
        try {
            // W-P2 实施方法抄袭语义分析
            List<RuleHit> wp2Hits = implementationMethodSemanticAnalyzer.analyze(tenderReviewData);
            allSemanticHits.addAll(wp2Hits);
            log.info("[TenderReviewWorkflow] W-P2 语义分析完成，命中数: {} - caseId: {}", wp2Hits.size(), caseId);
        } catch (Exception e) {
            log.error("[TenderReviewWorkflow] W-P2 语义分析异常 - caseId: {}, error: {}", caseId, e.getMessage());
        }

        try {
            // W-P3 服务承诺抄袭语义分析
            List<RuleHit> wp3Hits = serviceCommitmentSemanticAnalyzer.analyze(tenderReviewData);
            allSemanticHits.addAll(wp3Hits);
            log.info("[TenderReviewWorkflow] W-P3 语义分析完成，命中数: {} - caseId: {}", wp3Hits.size(), caseId);
        } catch (Exception e) {
            log.error("[TenderReviewWorkflow] W-P3 语义分析异常 - caseId: {}, error: {}", caseId, e.getMessage());
        }

        try {
            // W-M3 核心团队重叠语义分析
            List<RuleHit> wm3Hits = teamOverlapSemanticAnalyzer.analyze(tenderReviewData);
            allSemanticHits.addAll(wm3Hits);
            log.info("[TenderReviewWorkflow] W-M3 语义分析完成，命中数: {} - caseId: {}", wm3Hits.size(), caseId);
        } catch (Exception e) {
            log.error("[TenderReviewWorkflow] W-M3 语义分析异常 - caseId: {}, error: {}", caseId, e.getMessage());
        }

        log.info("[TenderReviewWorkflow] LLM 语义分析完成，总命中数: {} - caseId: {}", allSemanticHits.size(), caseId);
        return allSemanticHits;
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
