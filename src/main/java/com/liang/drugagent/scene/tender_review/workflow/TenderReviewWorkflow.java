package com.liang.drugagent.scene.tender_review.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.agent.chat.LLMChatService;
import com.liang.drugagent.scene.tender_review.model.ExemptionHit;
import com.liang.drugagent.scene.tender_review.model.RiskFusionResult;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewRagEvidence;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.CaseDataAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.CommercialTermsAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.CommercialCoordinationSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.ImplementationMethodSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.ProposalSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.RiskIdentificationSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.ServiceCommitmentSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.semantic.analyzer.TeamOverlapSemanticAnalyzer;
import com.liang.drugagent.scene.tender_review.service.EvidenceAssemblerService;
import com.liang.drugagent.scene.tender_review.service.ReportGenerationService;
import com.liang.drugagent.scene.tender_review.service.RiskFusionService;
import com.liang.drugagent.scene.tender_review.service.TenderReviewRagService;
import com.liang.drugagent.scene.tender_review.support.TenderExemptionEngine;
import com.liang.drugagent.scene.tender_review.support.TenderRuleEngine;
import com.liang.drugagent.agent.prompt.tender_review.validate.TenderReviewValidatePrompt;
import com.liang.drugagent.scene.tender_review.support.assembler.TenderReviewDataAssembler;
import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.EvidenceAssemblyResult;
import com.liang.drugagent.shared.model.ReviewReport;
import com.liang.drugagent.shared.model.report.ReportData;
import com.liang.drugagent.shared.model.ThinkingStep;
import com.liang.drugagent.shared.model.ThinkingStepEmitter;
import com.liang.drugagent.shared.model.ThinkingStepProgress;
import com.liang.drugagent.shared.model.WorkflowResult;
import com.liang.drugagent.shared.llm.LlmClient;
import com.liang.drugagent.shared.llm.LlmProviderType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final LlmClient llmClient;
    private final TenderRuleEngine tenderRuleEngine;
    private final TenderExemptionEngine tenderExemptionEngine;
    private final RiskFusionService riskFusionService;
    private final EvidenceAssemblerService evidenceAssemblerService;
    private final TenderReviewRagService tenderReviewRagService;
    private final ReportGenerationService reportGenerationService;
    private final ObjectMapper objectMapper;
    private final TenderReviewDataAssembler tenderReviewDataResolver;
    private final ProposalSemanticAnalyzer proposalSemanticAnalyzer;
    private final RiskIdentificationSemanticAnalyzer riskIdentificationSemanticAnalyzer;
    private final CommercialCoordinationSemanticAnalyzer commercialCoordinationSemanticAnalyzer;
    private final ImplementationMethodSemanticAnalyzer implementationMethodSemanticAnalyzer;
    private final ServiceCommitmentSemanticAnalyzer serviceCommitmentSemanticAnalyzer;
    private final CaseDataAnalyzer caseDataAnalyzer;
    private final CommercialTermsAnalyzer commercialTermsAnalyzer;
    private final TeamOverlapSemanticAnalyzer teamOverlapSemanticAnalyzer;
    private final Executor semanticAnalyzerExecutor;
    private final boolean l4ValidationEnabled;

    /**
     * LLM语义分析结果封装，包含命中的规则列表和各分析器的执行状态。
     */
    public record SemanticAnalysisResult(
            List<RuleHit> hits,
            Map<String, String> analyzerStatus
    ) {}

    public TenderReviewWorkflow(LLMChatService LLMChatService,
                                @Qualifier("dashScopeLlmClient") LlmClient llmClient,
                                TenderRuleEngine tenderRuleEngine,
                                TenderExemptionEngine tenderExemptionEngine,
                                RiskFusionService riskFusionService,
                                EvidenceAssemblerService evidenceAssemblerService,
                                TenderReviewRagService tenderReviewRagService,
                                ReportGenerationService reportGenerationService,
                                ObjectMapper objectMapper,
                                TenderReviewDataAssembler tenderReviewDataResolver,
                                ProposalSemanticAnalyzer proposalSemanticAnalyzer,
                                RiskIdentificationSemanticAnalyzer riskIdentificationSemanticAnalyzer,
                                CommercialCoordinationSemanticAnalyzer commercialCoordinationSemanticAnalyzer,
                                ImplementationMethodSemanticAnalyzer implementationMethodSemanticAnalyzer,
                                ServiceCommitmentSemanticAnalyzer serviceCommitmentSemanticAnalyzer,
                                CaseDataAnalyzer caseDataAnalyzer,
                                CommercialTermsAnalyzer commercialTermsAnalyzer,
                                TeamOverlapSemanticAnalyzer teamOverlapSemanticAnalyzer,
                                @Qualifier("semanticAnalyzerExecutor") Executor semanticAnalyzerExecutor,
                                @Value("${agent.tender-review.l4-validation-enabled:true}") boolean l4ValidationEnabled) {
        this.LLMChatService = LLMChatService;
        this.llmClient = llmClient;
        this.tenderRuleEngine = tenderRuleEngine;
        this.tenderExemptionEngine = tenderExemptionEngine;
        this.riskFusionService = riskFusionService;
        this.evidenceAssemblerService = evidenceAssemblerService;
        this.tenderReviewRagService = tenderReviewRagService;
        this.reportGenerationService = reportGenerationService;
        this.objectMapper = objectMapper;
        this.tenderReviewDataResolver = tenderReviewDataResolver;
        this.proposalSemanticAnalyzer = proposalSemanticAnalyzer;
        this.riskIdentificationSemanticAnalyzer = riskIdentificationSemanticAnalyzer;
        this.commercialCoordinationSemanticAnalyzer = commercialCoordinationSemanticAnalyzer;
        this.implementationMethodSemanticAnalyzer = implementationMethodSemanticAnalyzer;
        this.serviceCommitmentSemanticAnalyzer = serviceCommitmentSemanticAnalyzer;
        this.caseDataAnalyzer = caseDataAnalyzer;
        this.commercialTermsAnalyzer = commercialTermsAnalyzer;
        this.teamOverlapSemanticAnalyzer = teamOverlapSemanticAnalyzer;
        this.semanticAnalyzerExecutor = semanticAnalyzerExecutor;
        this.l4ValidationEnabled = l4ValidationEnabled;
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
        return executeWithProgress(context, ThinkingStepEmitter.noop());
    }

    /**
     * 带进度回调的标书审查工作流执行。
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
     * <p>每个阶段完成时会通过 emitter 推送进度更新事件。
     *
     * @param context Agent 上下文
     * @param emitter 进度发射器，用于推送思考步骤更新
     * @return 工作流执行结果
     */
    public WorkflowResult executeWithProgress(AgentChatContext context, ThinkingStepEmitter emitter) {
        // 初始化思考步骤列表
        List<ThinkingStep> completedSteps = new ArrayList<>();

        // 阶段1：数据加载
        ThinkingStep step1 = ThinkingStep.builder()
                .code("data_loading")
                .title("结构化加载")
                .detail("正在解析和加载标书文档数据")
                .type("EXECUTION")
                .status("PROCESSING")
                .order(1)
                .build();
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step1.getCode())
                .currentTitle(step1.getTitle())
                .currentStatus(step1.getStatus())
                .currentDetail(step1.getDetail())
                .currentStep(step1)
                .completedSteps(List.of())
                .finalResult(false)
                .build());

        TenderReviewData tenderReviewData = readTenderReviewData(context);
        if (tenderReviewData == null) {
            return buildInsufficientDataErrorResult(null);
        }

        int docCount = tenderReviewData.getDocuments() == null ? 0 : tenderReviewData.getDocuments().size();
        if (docCount < 2) {
            return buildInsufficientDataErrorResult(docCount);
        }

        // 阶段1完成
        step1.setStatus("COMPLETED");
        step1.setDetail("已完成加载 " + docCount + " 份标书文档");
        completedSteps.add(step1);
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step1.getCode())
                .currentTitle(step1.getTitle())
                .currentStatus(step1.getStatus())
                .currentDetail(step1.getDetail())
                .currentStep(step1)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        // 阶段2：规则命中分析
        ThinkingStep step2 = ThinkingStep.builder()
                .code("rule_analysis")
                .title("规则命中分析")
                .detail("正在执行确定性规则检测")
                .type("EXECUTION")
                .status("PROCESSING")
                .order(2)
                .build();
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step2.getCode())
                .currentTitle(step2.getTitle())
                .currentStatus(step2.getStatus())
                .currentDetail(step2.getDetail())
                .currentStep(step2)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        List<RuleHit> allHits = tenderRuleEngine.execute(tenderReviewData);

        // 阶段2完成
        step2.setStatus("COMPLETED");
        step2.setDetail("确定性规则检测完成，发现 " + allHits.size() + " 个命中");
        completedSteps.add(step2);
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step2.getCode())
                .currentTitle(step2.getTitle())
                .currentStatus(step2.getStatus())
                .currentDetail(step2.getDetail())
                .currentStep(step2)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        // 阶段3：LLM语义分析
        ThinkingStep step3 = ThinkingStep.builder()
                .code("semantic_analysis")
                .title("LLM语义分析")
                .detail("横渡专属模型分析中")
                .type("EXECUTION")
                .status("PROCESSING")
                .order(3)
                .build();
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step3.getCode())
                .currentTitle(step3.getTitle())
                .currentStatus(step3.getStatus())
                .currentDetail(step3.getDetail())
                .currentStep(step3)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        SemanticAnalysisResult semanticResult = executeSemanticAnalyzers(tenderReviewData);
        allHits.addAll(semanticResult.hits());
        Map<String, String> analyzerStatus = semanticResult.analyzerStatus();

        // 阶段3完成
        long successCount = analyzerStatus.values().stream().filter("SUCCESS"::equals).count();
        long failedCount = analyzerStatus.values().stream().filter(s -> "FAILED".equals(s) || "TIMEOUT".equals(s)).count();
        step3.setStatus("COMPLETED");
        step3.setDetail("LLM语义分析完成，成功 " + successCount + " 个，失败/超时 " + failedCount + " 个");
        completedSteps.add(step3);
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step3.getCode())
                .currentTitle(step3.getTitle())
                .currentStatus(step3.getStatus())
                .currentDetail(step3.getDetail())
                .currentStep(step3)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        // 阶段4：误报豁免
        ThinkingStep step4 = ThinkingStep.builder()
                .code("exemption")
                .title("误报豁免")
                .detail("正在应用免责判定引擎")
                .type("EXECUTION")
                .status("PROCESSING")
                .order(4)
                .build();
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step4.getCode())
                .currentTitle(step4.getTitle())
                .currentStatus(step4.getStatus())
                .currentDetail(step4.getDetail())
                .currentStep(step4)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        var exemptionResult = tenderExemptionEngine.apply(allHits, tenderReviewData);

        // 提取有效命中和豁免列表（供后续 LLM 结论生成使用）
        List<RuleHit> effectiveHits = exemptionResult.effectiveHits();
        List<ExemptionHit> exemptionHits = exemptionResult.exemptionHits();

        step4.setStatus("COMPLETED");
        step4.setDetail("误报豁免完成，" + exemptionResult.effectiveHits().size() + " 个有效命中，" + exemptionResult.exemptionHits().size() + " 个豁免");
        completedSteps.add(step4);
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step4.getCode())
                .currentTitle(step4.getTitle())
                .currentStatus(step4.getStatus())
                .currentDetail(step4.getDetail())
                .currentStep(step4)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        // 阶段5：法规知识检索
        ThinkingStep step5 = ThinkingStep.builder()
                .code("rag_legal_basis")
                .title("法规知识检索")
                .detail("正在根据有效风险命中检索法规、审查标准和案例依据")
                .type("EXECUTION")
                .status("PROCESSING")
                .order(5)
                .build();
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step5.getCode())
                .currentTitle(step5.getTitle())
                .currentStatus(step5.getStatus())
                .currentDetail(step5.getDetail())
                .currentStep(step5)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        TenderReviewRagEvidence ragEvidence = tenderReviewRagService.retrieveEvidence(
                effectiveHits,
                extractOrgId(context),
                context == null ? null : context.getTraceId()
        );
        analyzerStatus.put("RAG", ragEvidence == null ? "DEGRADED" : ragEvidence.getStatus());

        step5.setStatus("COMPLETED");
        step5.setDetail(buildRagStepDetail(ragEvidence));
        completedSteps.add(step5);
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step5.getCode())
                .currentTitle(step5.getTitle())
                .currentStatus(step5.getStatus())
                .currentDetail(step5.getDetail())
                .currentStep(step5)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        // 阶段6：风险融合
        ThinkingStep step6 = ThinkingStep.builder()
                .code("risk_fusion")
                .title("风险融合")
                .detail("正在计算综合风险评分")
                .type("EXECUTION")
                .status("PROCESSING")
                .order(6)
                .build();
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step6.getCode())
                .currentTitle(step6.getTitle())
                .currentStatus(step6.getStatus())
                .currentDetail(step6.getDetail())
                .currentStep(step6)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        RiskFusionResult fusionResult = riskFusionService.fuse(
                tenderReviewData,
                exemptionResult.effectiveHits(),
                exemptionResult.exemptionHits()
        );

        step6.setStatus("COMPLETED");
        step6.setDetail("风险融合完成，综合风险等级：" + fusionResult.getRiskLevel() + "，评分：" + fusionResult.getScore());
        completedSteps.add(step6);
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step6.getCode())
                .currentTitle(step6.getTitle())
                .currentStatus(step6.getStatus())
                .currentDetail(step6.getDetail())
                .currentStep(step6)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        // 阶段7：证据组装
        ThinkingStep step7 = ThinkingStep.builder()
                .code("evidence_assembly")
                .title("证据组装")
                .detail("正在组装风险证据链")
                .type("EXECUTION")
                .status("PROCESSING")
                .order(7)
                .build();
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step7.getCode())
                .currentTitle(step7.getTitle())
                .currentStatus(step7.getStatus())
                .currentDetail(step7.getDetail())
                .currentStep(step7)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        var evidenceAssemblyResult = evidenceAssemblerService.assemble(
                exemptionResult.effectiveHits(),
                exemptionResult.exemptionHits(),
                fusionResult,
                buildDocIdToNameMap(tenderReviewData)
        );
        appendRagEvidence(evidenceAssemblyResult, ragEvidence);

        step7.setStatus("COMPLETED");
        step7.setDetail("证据组装完成，共 " + evidenceAssemblyResult.getFlatItems().size() + " 条证据");
        completedSteps.add(step7);
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step7.getCode())
                .currentTitle(step7.getTitle())
                .currentStatus(step7.getStatus())
                .currentDetail(step7.getDetail())
                .currentStep(step7)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

        // 阶段8：报告生成
        ThinkingStep step8 = ThinkingStep.builder()
                .code("report_generation")
                .title("报告生成")
                .detail("正在生成审查报告")
                .type("EXECUTION")
                .status("PROCESSING")
                .order(8)
                .build();
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step8.getCode())
                .currentTitle(step8.getTitle())
                .currentStatus(step8.getStatus())
                .currentDetail(step8.getDetail())
                .currentStep(step8)
                .completedSteps(completedSteps)
                .finalResult(false)
                .build());

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
        result.setSummary(report.getOverview() != null ? report.getOverview().getSummary() : null);
        result.setRiskLevel(fusionResult.getRiskLevel());
        result.setScore(fusionResult.getScore() != null ? fusionResult.getScore() : 0);
        result.setSteps(List.of("场景路由", "结构化加载", "规则命中分析", "LLM语义分析", "误报豁免", "法规知识检索", "风险融合", "证据组装", "报告生成"));
        result.setReport(report);
        result.setEvidenceList(evidenceAssemblyResult.getFlatItems());
        result.setEvidenceGroups(evidenceAssemblyResult.getGroups());
        result.setAnalyzerStatus(analyzerStatus);

        // 生成结构化报告数据（供报告决策页面使用）
        ReportData reportData = reportGenerationService.generateReportData(
                tenderReviewData,
                allHits,
                effectiveHits,
                exemptionHits,
                fusionResult,
                evidenceAssemblyResult
        );
        result.setReportData(reportData);

        // 如果超过一半的 LLM 分析器失败，在报告中增加警告说明
        if (failedCount > 3) {
            String warning = "警告：LLM语义分析器有 " + failedCount + " 个执行失败，可能导致部分风险未被检测到，建议人工复核。";
            log.warn("[TenderReviewWorkflow] {}", warning);
            if (report.getExplanations() != null) {
                report.getExplanations().put("analyzer_warning", warning);
            }
        }

        // L4 校验
        if (l4ValidationEnabled) {
            ThinkingStep stepL4 = ThinkingStep.builder()
                    .code("l4_validation")
                    .title("L4输出校验")
                    .detail("正在进行输出合规性校验")
                    .type("EXECUTION")
                    .status("PROCESSING")
                    .order(9)
                    .build();
            emitter.emit(ThinkingStepProgress.builder()
                    .currentCode(stepL4.getCode())
                    .currentTitle(stepL4.getTitle())
                    .currentStatus(stepL4.getStatus())
                    .currentDetail(stepL4.getDetail())
                    .currentStep(stepL4)
                    .completedSteps(completedSteps)
                    .finalResult(false)
                    .build());

            validateAndNormalizeResult(result);

            stepL4.setStatus("COMPLETED");
            stepL4.setDetail("L4输出校验完成");
            completedSteps.add(stepL4);
        }

        // 所有阶段完成
        step8.setStatus("COMPLETED");
        step8.setDetail("审查报告生成完成");
        completedSteps.add(step8);

        // LLM 生成自然语言结论（识规则 + 建议）
        String llmConclusion = generateLlmConclusion(fusionResult, effectiveHits, exemptionHits, report);
        if (llmConclusion != null && !llmConclusion.isBlank()) {
            result.setSummary(llmConclusion);
            if (report.getOverview() != null) {
                report.getOverview().setSummary(llmConclusion);
            }
        }

        // 设置思考步骤到结果
        result.setThinkingSteps(new ArrayList<>(completedSteps));

        // 注入 documentIds（SSE 流需要）
        if (tenderReviewData != null && tenderReviewData.getDocuments() != null) {
            List<String> docIds = tenderReviewData.getDocuments().stream()
                    .map(doc -> doc.getDocumentId())
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toList());
            result.setDocumentIds(docIds);
        }

        // 生成会话标题（SSE 流需要）
        String riskLabel = fusionResult.getRiskLevel() != null ? fusionResult.getRiskLevel() : "未知";
        String sessionTitle = "标书审查[" + riskLabel + "]";
        if (tenderReviewData != null && tenderReviewData.getDocuments() != null
                && tenderReviewData.getDocuments().size() >= 2) {
            String name1 = tenderReviewData.getDocuments().get(0).getDocumentName();
            String name2 = tenderReviewData.getDocuments().get(1).getDocumentName();
            if (name1 != null && name2 != null) {
                sessionTitle = simplifyDocName(name1) + "与" + simplifyDocName(name2) + "审查";
            }
        }
        result.setSessionTitle(sessionTitle);

        // 发送最终结果
        emitter.emit(ThinkingStepProgress.builder()
                .currentCode(step8.getCode())
                .currentTitle(step8.getTitle())
                .currentStatus(step8.getStatus())
                .currentDetail(step8.getDetail())
                .currentStep(step8)
                .completedSteps(completedSteps)
                .finalResult(true)
                .result(result)
                .sessionTitle(result.getSessionTitle())
                .documentIds(result.getDocumentIds())
                .build());

        return result;
    }

    /**
     * 构建数据不足时的结构化错误结果。
     * 不再降级为通用闲聊，避免产生虚假审查报告。
     */
    private WorkflowResult buildInsufficientDataErrorResult(Integer docCount) {
        String errorMessage;
        if (docCount == null || docCount == 0) {
            errorMessage = "请上传至少2份标书文件，当前未检测到有效的标书文档。";
        } else {
            errorMessage = "请上传至少2份标书文件，当前仅检测到 " + docCount + " 份文档。";
        }

        WorkflowResult result = WorkflowResult.of(SceneEnum.TENDER_REVIEW, errorMessage);
        result.setRiskLevel("UNKNOWN");
        result.setScore(null);
        result.setSteps(List.of("场景路由", "数据校验"));
        result.setEvidenceList(List.of(
                new EvidenceItem("data_validation", errorMessage, "system")
        ));
        result.getAnalyzerStatus().put("workflow", "DATA_INSUFFICIENT");
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
        SemanticAnalysisResult semanticResult = executeSemanticAnalyzers(tenderReviewData);
        allHits.addAll(semanticResult.hits());
        Map<String, String> analyzerStatus = semanticResult.analyzerStatus();

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
                fusionResult,
                buildDocIdToNameMap(tenderReviewData)
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
        result.setSummary(report.getOverview() != null ? report.getOverview().getSummary() : null);
        result.setRiskLevel(fusionResult.getRiskLevel());
        result.setScore(fusionResult.getScore() != null ? fusionResult.getScore() : 0);
        result.setSteps(List.of("场景路由", "结构化加载", "规则命中分析", "LLM语义分析", "误报豁免", "风险融合", "证据组装", "报告生成"));
        result.setReport(report);
        result.setEvidenceList(evidenceAssemblyResult.getFlatItems());
        result.setEvidenceGroups(evidenceAssemblyResult.getGroups());
        result.setAnalyzerStatus(analyzerStatus);

        // 如果超过一半的 LLM 分析器失败，在报告中增加警告说明
        long failedCount = analyzerStatus.values().stream().filter("FAILED"::equals).count();
        if (failedCount > 3) {
            String warning = "警告：LLM语义分析器有 " + failedCount + " 个执行失败（" + analyzerStatus.entrySet().stream()
                    .filter(e -> "FAILED".equals(e.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining("、")) + "），可能导致部分风险未被检测到，建议人工复核。";
            log.warn("[TenderReviewWorkflow] {}", warning);
            // 在报告的explanations中追加警告信息
            if (report.getExplanations() != null) {
                report.getExplanations().put("analyzer_warning", warning);
            }
        }

        // L4 校验：检查输出合规性并生成规范的 answer（可配置开关）
        if (l4ValidationEnabled) {
            validateAndNormalizeResult(result);
        } else {
            log.info("[TenderReviewWorkflow] L4 校验已禁用，跳过校验阶段");
        }

        return result;
    }

    /**
     * 执行 LLM 语义分析器集合。
     * 对 W-P1、W-P4、W-M8（W-P2、W-P3、W-M3）规则进行 LLM 语义补强判断。
     * 6个分析器并行执行，每个分析器独立超时30秒，超时则标记为FAILED。
     * 各分析器执行状态会记录在返回结果的 analyzerStatus 中。
     *
     * @param tenderReviewData 标书审查数据
     * @return 语义分析结果，包含命中的规则列表和各分析器的执行状态
     */
    private SemanticAnalysisResult executeSemanticAnalyzers(TenderReviewData tenderReviewData) {
        String caseId = tenderReviewData.getACase() != null ? tenderReviewData.getACase().getCaseId() : "unknown";
        log.info("[TenderReviewWorkflow] 开始执行 LLM 语义分析（8个分析器并行） - caseId: {}", caseId);

        // 使用线程安全的Map记录各分析器状态
        Map<String, String> analyzerStatus = new ConcurrentHashMap<>();

        // 收集所有语义分析命中结果
        List<RuleHit> allSemanticHits = Collections.synchronizedList(new ArrayList<>());

        // 定义6个分析器的名称与实现
        List<AnalyzerTask> tasks = List.of(
                new AnalyzerTask("W-P1", data -> proposalSemanticAnalyzer.analyze(data)),
                new AnalyzerTask("W-P4", data -> riskIdentificationSemanticAnalyzer.analyze(data)),
                new AnalyzerTask("W-M8", data -> commercialCoordinationSemanticAnalyzer.analyze(data)),
                new AnalyzerTask("W-P2", data -> implementationMethodSemanticAnalyzer.analyze(data)),
                new AnalyzerTask("W-P3", data -> serviceCommitmentSemanticAnalyzer.analyze(data)),
                new AnalyzerTask("W-M3", data -> teamOverlapSemanticAnalyzer.analyze(data)),
                new AnalyzerTask("W-M6", data -> commercialTermsAnalyzer.analyze(data)),
                new AnalyzerTask("W-P6", data -> caseDataAnalyzer.analyze(data))
        );

        // 并行执行所有8个分析器，每个独立超时30秒
        List<CompletableFuture<Void>> futures = tasks.stream()
                .map(task -> CompletableFuture.runAsync(() -> {
                    try {
                        log.info("[TenderReviewWorkflow] 开始执行分析器 {} - caseId: {}", task.name, caseId);
                        analyzerStatus.put(task.name, "RUNNING");
                        List<RuleHit> hits = task.analyzer.apply(tenderReviewData);
                        allSemanticHits.addAll(hits);
                        analyzerStatus.put(task.name, "SUCCESS");
                        log.info("[TenderReviewWorkflow] 分析器 {} 执行成功，命中数: {} - caseId: {}", task.name, hits.size(), caseId);
                    } catch (Exception e) {
                        analyzerStatus.put(task.name, "FAILED");
                        log.error("[TenderReviewWorkflow] 分析器 {} 执行异常 - caseId: {}, error: {}", task.name, caseId, e.getMessage());
                    }
                }, semanticAnalyzerExecutor)
                        .orTimeout(30, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            analyzerStatus.put(task.name, "TIMEOUT");
                            log.warn("[TenderReviewWorkflow] 分析器 {} 执行超时（30秒） - caseId: {}", task.name, caseId);
                            return null;
                        }))
                .collect(Collectors.toList());

        // 等待所有分析器完成（或超时）
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("[TenderReviewWorkflow] LLM 语义分析完成，总命中数: {} - caseId: {}, 状态: {}",
                allSemanticHits.size(), caseId, analyzerStatus);
        return new SemanticAnalysisResult(allSemanticHits, analyzerStatus);
    }

    /**
     * 分析器任务封装，内部类。
     * 用于将分析器名称与其实施方法绑定。
     */
    private static class AnalyzerTask {
        final String name;
        final java.util.function.Function<TenderReviewData, List<RuleHit>> analyzer;

        AnalyzerTask(String name, java.util.function.Function<TenderReviewData, List<RuleHit>> analyzer) {
            this.name = name;
            this.analyzer = analyzer;
        }
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

    /**
     * 从上下文元数据中读取机构标识。
     * RAG 检索必须带 orgId，缺失时直接跳过知识增强，避免全库裸搜。
     */
    private String extractOrgId(AgentChatContext context) {
        if (context == null || context.getMetadata() == null) {
            return null;
        }
        Object orgId = context.getMetadata().get("orgId");
        return orgId == null ? null : orgId.toString();
    }

    private String buildRagStepDetail(TenderReviewRagEvidence ragEvidence) {
        if (ragEvidence == null) {
            return "法规知识检索降级，未影响风险判断主流程";
        }
        String status = ragEvidence.getStatus();
        if ("SUPPORTED".equals(status)) {
            return "法规知识检索完成，补充 " + ragEvidence.getHitCount() + " 条法规/审查依据";
        }
        if ("SKIPPED".equals(status)) {
            return "法规知识检索跳过：" + ragEvidence.getReason();
        }
        if ("NO_HIT".equals(status)) {
            return "法规知识检索未命中，风险判断继续使用规则与语义证据";
        }
        return "法规知识检索降级：" + ragEvidence.getReason();
    }

    private void appendRagEvidence(EvidenceAssemblyResult evidenceAssemblyResult, TenderReviewRagEvidence ragEvidence) {
        if (evidenceAssemblyResult == null || ragEvidence == null || !ragEvidence.hasEvidence()) {
            return;
        }
        if (evidenceAssemblyResult.getGroups() == null) {
            evidenceAssemblyResult.setGroups(new ArrayList<>());
        }
        if (evidenceAssemblyResult.getFlatItems() == null) {
            evidenceAssemblyResult.setFlatItems(new ArrayList<>());
        }
        evidenceAssemblyResult.getGroups().add(ragEvidence.getGroup());
        evidenceAssemblyResult.getFlatItems().addAll(ragEvidence.getItems());
    }

    /**
     * L4 校验：验证工作流输出是否合规，并生成规范的 answer。
     *
     * <p>职责：
     * <ul>
     *   <li>检查关键字段是否缺失</li>
     *   <li>检查 answer 是否包含不合规内容</li>
     *   <li>检查结构化数据与文本描述是否一致</li>
     *   <li>生成规范的 summary 和 answer</li>
     *   <li>识别并报告潜在问题</li>
     * </ul>
     *
     * @param result 工作流结果（会被直接修改）
     */
    private void validateAndNormalizeResult(WorkflowResult result) {
        try {
            String workflowResultJson = objectMapper.writeValueAsString(result);
            String userMessage = TenderReviewValidatePrompt.buildUserMessage(
                    result.getScene() != null ? result.getScene().name() : "TENDER_REVIEW",
                    "规则执行完成，进入输出校验阶段",
                    workflowResultJson
            );

            LlmRequest request = LlmRequest.builder()
                    .provider(LlmProviderType.DASHSCOPE)
                    .model("qwen3.5-plus")
                    .sessionId(null)
                    .systemPrompt(TenderReviewValidatePrompt.SYSTEM_PROMPT)
                    .messages(List.of(LlmRequest.ChatMessage.builder()
                            .role("user")
                            .content(userMessage)
                            .build()))
                    .temperature(0.2f)
                    .responseFormat("json_schema:" + objectMapper.writeValueAsString(
                            TenderReviewValidatePrompt.getResponseFormat()))
                    .thinkingEnabled(false)
                    .stream(false)
                    .build();

            LlmResponse response = llmClient.chat(request);
            String validatedContent = response.getContent();

            log.info("[TenderReviewWorkflow] L4 校验完成，原始响应长度: {}", validatedContent != null ? validatedContent.length() : 0);

            if (validatedContent == null || validatedContent.isBlank()) {
                log.warn("[TenderReviewWorkflow] L4 校验返回内容为空，保持原始输出");
                return;
            }

            // 解析 JSON 响应
            var validationResult = objectMapper.readTree(validatedContent);
            boolean passed = validationResult.has("passed") && validationResult.get("passed").asBoolean();

            if (validationResult.has("summary") && !validationResult.get("summary").isNull()) {
                result.setSummary(validationResult.get("summary").asText());
            }

            if (validationResult.has("answer") && !validationResult.get("answer").isNull()) {
                result.setAnswer(validationResult.get("answer").asText());
            }

            if (result.getReport() != null) {
                if (validationResult.has("managementSummary") && validationResult.get("managementSummary").isArray()) {
                    List<String> managementSummary = new ArrayList<>();
                    validationResult.get("managementSummary").forEach(item -> managementSummary.add(item.asText()));
                    result.getReport().setManagementSummary(managementSummary);
                }

                if (validationResult.has("suggestedActions") && validationResult.get("suggestedActions").isArray()) {
                    List<String> suggestedActions = new ArrayList<>();
                    validationResult.get("suggestedActions").forEach(item -> suggestedActions.add(item.asText()));
                    result.getReport().setRecommendedActions(suggestedActions);
                }
            }

            if (!passed && validationResult.has("warnings")) {
                List<String> warnings = new java.util.ArrayList<>();
                validationResult.get("warnings").forEach(w -> warnings.add(w.asText()));
                log.warn("[TenderReviewWorkflow] L4 校验未通过，warnings: {}", warnings);
                if (result.getReport() != null) {
                    if (result.getReport().getExplanations() == null) {
                        result.getReport().setExplanations(new LinkedHashMap<>());
                    }
                    result.getReport().getExplanations().put("output_validation_warning", String.join("；", warnings));
                }
            }

            log.info("[TenderReviewWorkflow] L4 校验完成，passed={}", passed);

        } catch (Exception e) {
            log.error("[TenderReviewWorkflow] L4 校验异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 简化文档名，用于标题展示。
     */
    private String simplifyDocName(String documentName) {
        if (documentName == null || documentName.isBlank()) {
            return "";
        }
        return documentName.trim()
                .replaceAll("\\.[A-Za-z0-9]{1,6}$", "")
                .replaceAll("^投标人[A-Za-z0-9_\\-\\s]+[\\-_]", "")
                .replaceAll("[\\-_]?(?:标书|投标文件|响应文件|商务标|技术标)$", "");
    }

    /**
     * 构建文档ID到可读文件名的映射。
     * 用于在证据组装时将原始文档ID替换为用户友好的文件名。
     */
    private Map<String, String> buildDocIdToNameMap(TenderReviewData data) {
        Map<String, String> map = new LinkedHashMap<>();
        if (data != null && data.getDocuments() != null) {
            for (TenderDocument doc : data.getDocuments()) {
                if (doc.getDocumentId() != null && doc.getDocumentName() != null) {
                    map.put(doc.getDocumentId(), simplifyDocName(doc.getDocumentName()));
                }
            }
        }
        return map;
    }

    /**
     * 调用 LLM 生成自然语言结论。
     * 结合命中的规则、风险等级和证据，生成最可能的规则判断 + 处置建议。
     *
     * @param fusionResult 风险融合结果
     * @param effectiveHits 有效命中的规则列表
     * @param exemptionHits 豁免的规则列表
     * @param report 审查报告
     * @return LLM 生成的结论文本，异常时返回 null
     */
    private String generateLlmConclusion(RiskFusionResult fusionResult,
                                          List<RuleHit> effectiveHits,
                                          List<ExemptionHit> exemptionHits,
                                          ReviewReport report) {
        try {
            String riskLevel = fusionResult != null ? fusionResult.getRiskLevel() : "UNKNOWN";
            Integer score = fusionResult != null ? fusionResult.getScore() : 0;

            // 提取最可能的规则（按权重排序取前3）
            List<RuleHit> topHits = effectiveHits != null
                    ? effectiveHits.stream()
                        .sorted((a, b) -> {
                            Integer wa = a.getAdjustedWeight() != null ? a.getAdjustedWeight() : (a.getWeight() != null ? a.getWeight() : 0);
                            Integer wb = b.getAdjustedWeight() != null ? b.getAdjustedWeight() : (b.getWeight() != null ? b.getWeight() : 0);
                            return Integer.compare(wb, wa);
                        })
                        .limit(3)
                        .toList()
                    : List.of();

            String ruleInfo = topHits.stream()
                    .map(h -> {
                        String name = h.getRuleName() != null ? h.getRuleName() : h.getRuleCode();
                        String summary = h.getTriggerSummary();
                        return name + (summary != null ? "（" + summary + "）" : "");
                    })
                    .toList()
                    .stream()
                    .collect(Collectors.joining("；"));

            String evidenceInfo = "";
            if (report != null && report.getRiskItems() != null && !report.getRiskItems().isEmpty()) {
                evidenceInfo = report.getRiskItems().stream()
                        .limit(2)
                        .map(item -> item.getTitle() + "[" + item.getRiskLevel() + "]")
                        .toList()
                        .stream()
                        .collect(Collectors.joining("、"));
            }

            String prompt = String.format("""
                    你是一位医药监管领域的标书审查专家。根据以下标书审查结果，生成一句精炼的自然语言结论，要求：
                    1. 指出最可能命中的1-2个规则（如"W-M2规则"或"技术方案高度相似"）
                    2. 给出明确的处置建议
                    3. 总字数控制在50字以内
                    4. 语言专业但易懂，不罗列数字

                    审查信息：
                    - 风险等级：%s
                    - 风险评分：%d
                    %s
                    %s

                    结论格式示例：
                    "技术方案与服务承诺高度吻合，疑似串标，建议立即人工复核。"
                    "资质文件存在关联，多个投标人疑似班底重叠，建议进一步核查。"

                    请直接输出结论，不要解释。
                    """,
                    riskLevel,
                    score != null ? score : 0,
                    ruleInfo.isEmpty() ? "" : "- 最可能命中的规则：" + ruleInfo,
                    evidenceInfo.isEmpty() ? "" : "- 关键证据：" + evidenceInfo
            );

            LlmRequest request = LlmRequest.builder()
                    .provider(LlmProviderType.DASHSCOPE)
                    .model("qwen-plus-2025-07-28")
                    .messages(List.of(LlmRequest.ChatMessage.builder()
                            .role("user")
                            .content(prompt)
                            .build()))
                    .temperature(0.3f)
                    .stream(false)
                    .build();

            LlmResponse response = llmClient.chat(request);
            if (response != null && response.getContent() != null && !response.getContent().isBlank()) {
                String conclusion = response.getContent().trim();
                log.info("[TenderReviewWorkflow] LLM 结论生成成功: {}", conclusion);
                return conclusion;
            }
        } catch (Exception e) {
            log.warn("[TenderReviewWorkflow] LLM 结论生成失败: {}", e.getMessage());
        }
        return null;
    }

}
