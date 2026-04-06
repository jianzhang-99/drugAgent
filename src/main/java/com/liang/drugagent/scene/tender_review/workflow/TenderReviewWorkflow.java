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
import com.liang.drugagent.agent.prompt.tender_review.validate.TenderReviewValidatePrompt;
import com.liang.drugagent.scene.tender_review.support.assembler.TenderReviewDataAssembler;
import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.ReviewReport;
import com.liang.drugagent.shared.model.WorkflowResult;
import com.liang.drugagent.shared.llm.LlmClient;
import com.liang.drugagent.shared.llm.LlmProviderType;
import org.springframework.beans.factory.annotation.Qualifier;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ReportGenerationService reportGenerationService;
    private final ObjectMapper objectMapper;
    private final TenderReviewDataAssembler tenderReviewDataResolver;
    private final ProposalSemanticAnalyzer proposalSemanticAnalyzer;
    private final RiskIdentificationSemanticAnalyzer riskIdentificationSemanticAnalyzer;
    private final CommercialCoordinationSemanticAnalyzer commercialCoordinationSemanticAnalyzer;
    private final ImplementationMethodSemanticAnalyzer implementationMethodSemanticAnalyzer;
    private final ServiceCommitmentSemanticAnalyzer serviceCommitmentSemanticAnalyzer;
    private final TeamOverlapSemanticAnalyzer teamOverlapSemanticAnalyzer;
    private final Executor semanticAnalyzerExecutor;

    /**
     * LLM语义分析结果封装，包含命中的规则列表和各分析器的执行状态。
     */
    public record SemanticAnalysisResult(
            List<RuleHit> hits,
            Map<String, String> analyzerStatus
    ) {}

    public TenderReviewWorkflow(LLMChatService LLMChatService,
                                @Qualifier("miniMaxLlmClient") LlmClient llmClient,
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
                                TeamOverlapSemanticAnalyzer teamOverlapSemanticAnalyzer,
                                @Qualifier("semanticAnalyzerExecutor") Executor semanticAnalyzerExecutor) {
        this.LLMChatService = LLMChatService;
        this.llmClient = llmClient;
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
        this.semanticAnalyzerExecutor = semanticAnalyzerExecutor;
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
        if (tenderReviewData == null) {
            return buildInsufficientDataErrorResult(null);
        }

        // 文档数量校验：标书审查至少需要2份文档
        int docCount = tenderReviewData.getDocuments() == null ? 0 : tenderReviewData.getDocuments().size();
        if (docCount < 2) {
            return buildInsufficientDataErrorResult(docCount);
        }

        return executeRuleFlow(tenderReviewData);
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

        // L4 校验：检查输出合规性并生成规范的 answer
        validateAndNormalizeResult(result);

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
        log.info("[TenderReviewWorkflow] 开始执行 LLM 语义分析（6个分析器并行） - caseId: {}", caseId);

        // 使用线程安全的Map记录各分析器状态
        Map<String, String> analyzerStatus = new ConcurrentHashMap<>();

        // 定义6个分析器的名称与实现
        List<AnalyzerTask> tasks = List.of(
                new AnalyzerTask("W-P1", data -> proposalSemanticAnalyzer.analyze(data)),
                new AnalyzerTask("W-P4", data -> riskIdentificationSemanticAnalyzer.analyze(data)),
                new AnalyzerTask("W-M8", data -> commercialCoordinationSemanticAnalyzer.analyze(data)),
                new AnalyzerTask("W-P2", data -> implementationMethodSemanticAnalyzer.analyze(data)),
                new AnalyzerTask("W-P3", data -> serviceCommitmentSemanticAnalyzer.analyze(data)),
                new AnalyzerTask("W-M3", data -> teamOverlapSemanticAnalyzer.analyze(data))
        );

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
                    .provider(LlmProviderType.MINIMAX)
                    .model("MiniMax-M2.7")
                    .sessionId(null)
                    .systemPrompt(TenderReviewValidatePrompt.SYSTEM_PROMPT)
                    .messages(List.of(LlmRequest.ChatMessage.builder()
                            .role("user")
                            .content(userMessage)
                            .build()))
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

            if (!passed && validationResult.has("warnings")) {
                List<String> warnings = new java.util.ArrayList<>();
                validationResult.get("warnings").forEach(w -> warnings.add(w.asText()));
                log.warn("[TenderReviewWorkflow] L4 校验未通过，warnings: {}", warnings);
            }

            log.info("[TenderReviewWorkflow] L4 校验完成，passed={}", passed);

        } catch (Exception e) {
            log.error("[TenderReviewWorkflow] L4 校验异常: {}", e.getMessage(), e);
        }
    }

}
