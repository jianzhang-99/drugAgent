package com.liang.drugagent.scene.tender_review.tool;

import com.liang.drugagent.agent.chat.AgentChatContext;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.service.TenderCaseService;
import com.liang.drugagent.scene.tender_review.service.TenderDocumentParseService;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmResponse;
import com.liang.drugagent.shared.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/**
 * 标书审查工具编排器。
 *
 * <p>负责"LLM 调工具 -> workflow 执行 -> LLM 润色"的完整编排链路。</p>
 *
 * <p>核心流程：
 * <ol>
 *   <li>LLM 判断需要执行标书审查，返回 tool call</li>
 *   <li>解析 tool call 参数，构建 ReviewTenderToolRequest</li>
 *   <li>调用 ReviewTenderTool 执行实际审查</li>
 *   <li>将审查结果交由 LLM 润色生成最终回复</li>
 * </ol>
 *
 * <p>该编排器解耦了 LLM 决策、工具执行、结果润色三个环节，便于独立演进和测试。</p>
 *
 * @author liangjiajian
 * @see ReviewTenderTool
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenderReviewToolOrchestrator {

    private final ReviewTenderTool reviewTenderTool;
    private final ReviewTenderToolResultMapper resultMapper;
    private final LlmService llmService;
    private final TenderCaseService tenderCaseService;
    private final TenderDocumentParseService tenderDocumentParseService;

    /**
     * 处理标书审查场景的请求。
     *
     * <p>该方法是同步入口，由 {@link com.liang.drugagent.agent.chat.AgentChatService}
     * 在识别到 TENDER_REVIEW 场景时调用。</p>
     *
     * <p>执行流程：
     * <ol>
     *   <li>构建工具调用请求</li>
     *   <li>执行 ReviewTenderTool</li>
     *   <li>将工具结果转换为 AgentChatResp</li>
     * </ol>
     *
     * @param context  Agent 上下文
     * @param decision 路由决策结果
     * @return AI 响应结果
     */
    public AgentChatResp handle(
            AgentChatContext context,
            com.liang.drugagent.shared.domain.model.WorkflowRouteDecision decision) {
        log.info("[TenderReviewToolOrchestrator] 开始处理标书审查请求: sessionId={}, query={}",
                context.getSessionId(), context.getQuery());

        try {
            // 1. 解析 TenderReviewData
            TenderReviewData tenderReviewData = resolveTenderReviewData(context);

            // 2. 如果没有审查数据，降级为通用对话
            if (tenderReviewData == null || tenderReviewData.getDocuments() == null || tenderReviewData.getDocuments().isEmpty()) {
                log.warn("[TenderReviewToolOrchestrator] 无标书审查数据，降级到工作流");
                return resultMapper.toDrugAgentResp(
                        com.liang.drugagent.scene.tender_review.tool.ReviewTenderToolResult.failure("无标书审查数据"),
                        null,
                        context.getTraceId(),
                        "标书审查-数据缺失"
                );
            }

            // 3. 构建工具请求
            ReviewTenderToolRequest toolRequest = ReviewTenderToolRequest.of(
                    context.getSessionId(),
                    context.getFileIds(),
                    extractReviewFocus(context.getQuery()),
                    extractUserInstruction(context.getQuery()),
                    shouldGenerateReport(context.getQuery())
            );

            // 4. 执行工具
            ReviewTenderToolResult toolResult = reviewTenderTool.reviewTender(toolRequest);

            // 5. 转换为 AgentChatResp
            return resultMapper.toDrugAgentResp(
                    toolResult,
                    context.getSessionId(),
                    context.getTraceId(),
                    decision != null ? decision.getReason() : "标书审查工具执行"
            );

        } catch (Exception e) {
            log.error("[TenderReviewToolOrchestrator] 处理标书审查请求失败: {}", e.getMessage(), e);
            return resultMapper.toDrugAgentResp(
                    ReviewTenderToolResult.failure("标书审查执行失败: " + e.getMessage()),
                    null,
                    context.getTraceId(),
                    "标书审查异常"
            );
        }
    }

    /**
     * 从上下文中解析 TenderReviewData。
     */
    private TenderReviewData resolveTenderReviewData(AgentChatContext context) {
        if (context == null || context.getMetadata() == null) {
            return null;
        }
        Object rawData = context.getMetadata().get("tenderReviewData");
        if (rawData == null) {
            return null;
        }
        if (rawData instanceof TenderReviewData) {
            return (TenderReviewData) rawData;
        }
        return null;
    }

    /**
     * 从查询中提取审查重点。
     */
    private String extractReviewFocus(String query) {
        if (query == null) {
            return null;
        }
        if (query.contains("围标") || query.contains("串标")) {
            return "围标风险";
        }
        if (query.contains("技术方案") || query.contains("雷同") || query.contains("抄袭")) {
            return "技术方案雷同";
        }
        if (query.contains("商务条款") || query.contains("商务")) {
            return "商务条款";
        }
        return null;
    }

    /**
     * 从查询中提取用户补充说明。
     */
    private String extractUserInstruction(String query) {
        return null;
    }

    /**
     * 判断是否需要生成完整报告。
     */
    private Boolean shouldGenerateReport(String query) {
        if (query == null) {
            return true;
        }
        return query.contains("完整报告") || query.contains("详细报告") || query.contains("正式报告");
    }

    /**
     * 工具名称（与 ReviewTenderTool 一致）。
     */
    public static final String TOOL_NAME = "review_tender";

    /**
     * 执行完整的工具化标书审查流程。
     *
     * <p>典型调用场景：
     * <pre>
     * 1. 用户上传标书文件并请求审查
     * 2. Agent 识别到 TENDER_REVIEW 场景
     * 3. Orchestrator 构建 ToolRequest 并执行审查
     * 4. LLM 根据审查结果润色生成最终回复
     * </pre>
     *
     * @param context     Agent 上下文（包含用户查询、会话信息）
     * @param files       上传的标书文件
     * @param submittedBy 提交人
     * @return 编排结果（包含润色后的回复）
     */
    public OrchestrationResult orchestrate(AgentChatContext context,
                                          MultipartFile[] files,
                                          String submittedBy) {
        log.info("[TenderReviewToolOrchestrator] 开始编排标书审查流程, sessionId={}, fileCount={}",
                context.getSessionId(), files != null ? files.length : 0);

        try {
            // 构建 TenderReviewData
            TenderReviewData tenderReviewData = buildTenderReviewData(context, files, submittedBy);

            // 如果没有文件或无法构建审查数据，返回失败
            if (tenderReviewData == null || tenderReviewData.getDocuments() == null || tenderReviewData.getDocuments().isEmpty()) {
                log.warn("[TenderReviewToolOrchestrator] 构建标书审查数据失败");
                return OrchestrationResult.failure("无法构建标书审查数据，请确保已上传文件");
            }

            // 使用 orchestrateWithData 执行完整流程
            return orchestrateWithData(context, tenderReviewData);

        } catch (IllegalArgumentException e) {
            log.warn("[TenderReviewToolOrchestrator] 请求参数不完整: {}", e.getMessage());
            return OrchestrationResult.failure("请求参数不完整: " + e.getMessage());
        } catch (Exception e) {
            log.error("[TenderReviewToolOrchestrator] 编排流程执行失败", e);
            return OrchestrationResult.failure("标书审查执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行工具化审查（已有审查数据时）。
     *
     * <p>适用于 Agent 已经有 TenderReviewData 的场景，
     * 比如用户已经在当前会话中上传过文件。</p>
     *
     * @param context         Agent 上下文
     * @param tenderReviewData 预构建的审查数据
     * @return 编排结果
     */
    public OrchestrationResult orchestrateWithData(AgentChatContext context,
                                                   TenderReviewData tenderReviewData) {
        log.info("[TenderReviewToolOrchestrator] 使用已有数据开始编排, sessionId={}",
                context.getSessionId());

        long startTime = System.currentTimeMillis();

        try {
            // 构建 Tool Request（使用 record 的 compact 构造）
            ReviewTenderToolRequest toolRequest = ReviewTenderToolRequest.of(
                    context.getSessionId(),
                    context.getFileIds() != null ? new ArrayList<>(context.getFileIds()) : new ArrayList<>(),
                    null,  // reviewFocus
                    null,  // userInstruction
                    true   // needStructuredReport
            );

            // 执行工具（带预加载的 TenderReviewData）
            ReviewTenderToolResult toolResult = reviewTenderTool.reviewTender(toolRequest, tenderReviewData);

            if (!toolResult.success()) {
                return OrchestrationResult.failure(toolResult.message());
            }

            // LLM 润色
            String polishedAnswer = polishResult(toolResult, context);

            return OrchestrationResult.builder()
                    .success(true)
                    .rawResult(toolResult)
                    .polishedAnswer(polishedAnswer)
                    .caseId(toolResult.caseId())
                    .riskLevel(toolResult.riskLevel())
                    .score(toolResult.score())
                    .report(toolResult.report())
                    .evidenceList(toolResult.evidenceList())
                    .steps(toolResult.steps())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("[TenderReviewToolOrchestrator] 使用数据编排失败", e);
            return OrchestrationResult.failure("标书审查执行失败: " + e.getMessage());
        }
    }

    /**
     * 仅执行工具（不进行 LLM 润色）。
     *
     * <p>适用于调试、测试、或需要自行处理润色的场景。</p>
     *
     * @param context Agent 上下文
     * @param files   上传的标书文件
     * @param submittedBy 提交人
     * @return 工具原始执行结果
     */
    public ReviewTenderToolResult executeToolOnly(AgentChatContext context,
                                                   MultipartFile[] files,
                                                   String submittedBy) {
        try {
            TenderReviewData tenderReviewData = buildTenderReviewData(context, files, submittedBy);
            ReviewTenderToolRequest toolRequest = ReviewTenderToolRequest.of(
                    context.getSessionId(),
                    context.getFileIds() != null ? new ArrayList<>(context.getFileIds()) : new ArrayList<>(),
                    null,
                    null,
                    true
            );
            return reviewTenderTool.reviewTender(toolRequest, tenderReviewData);
        } catch (IOException e) {
            log.error("[TenderReviewToolOrchestrator] 仅执行工具失败: {}", e.getMessage(), e);
            return ReviewTenderToolResult.failure("构建审查数据失败: " + e.getMessage());
        }
    }

    /**
     * 构建 TenderReviewData。
     *
     * <p>包含以下步骤：
     * <ol>
     *   <li>创建标书审查 Case</li>
     *   <li>解析上传的文档</li>
     *   <li>构建完整的 TenderReviewData</li>
     * </ol>
     *
     * @return TenderReviewData 审查数据对象
     */
    private TenderReviewData buildTenderReviewData(AgentChatContext context,
                                                  MultipartFile[] files,
                                                  String submittedBy) throws IOException {
        // 收集文件名
        List<String> filenames = new ArrayList<>();
        for (MultipartFile file : files) {
            filenames.add(Optional.ofNullable(file.getOriginalFilename()).orElse("unnamed"));
        }

        // 创建标书审查 Case
        var caseResp = tenderCaseService.createCase(
                com.liang.drugagent.controller.domain.request.tender_review.TenderCaseCreateReq.builder()
                        .filenames(filenames)
                        .submittedBy(submittedBy)
                        .build());

        // 初始化数据结构
        List<com.liang.drugagent.scene.tender_review.model.TenderDocument> documents = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Block> blocks = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Field> fields = new ArrayList<>();
        com.liang.drugagent.scene.tender_review.model.ExtractionMeta extractionMeta =
                new com.liang.drugagent.scene.tender_review.model.ExtractionMeta();
        extractionMeta.setSchemaVersion("tender-review-struct-v1");
        extractionMeta.setParserVersion("orchestrator-v1");
        extractionMeta.setParseSuccess(Boolean.TRUE);

        // 解析每个文档
        List<String> documentIds = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            String docId = caseResp.getDocumentIds().get(i);
            documentIds.add(docId);

            byte[] bytes = file.getBytes();
            // 存储文件内容
            tenderCaseService.storeFileContent(docId, bytes);

            // 解析文档
            var parseResult = tenderDocumentParseService.parseDocument(
                    docId,
                    filenames.get(i),
                    new ByteArrayInputStream(bytes));

            // 收集解析结果
            documents.add(buildTenderDocument(caseResp.getCaseId(), docId, filenames.get(i)));
            blocks.addAll(Optional.ofNullable(parseResult.getParagraphBlocks()).orElse(List.of()));
            blocks.addAll(Optional.ofNullable(parseResult.getTableBlocks()).orElse(List.of()));
            fields.addAll(Optional.ofNullable(parseResult.getFields()).orElse(List.of()));

            if (Boolean.FALSE.equals(parseResult.getParseSuccess())) {
                extractionMeta.setParseSuccess(Boolean.FALSE);
            }
        }

        // 构建审查数据并返回
        TenderReviewData tenderReviewData = new TenderReviewData();
        tenderReviewData.setACase(buildTenderCase(caseResp.getCaseId(), submittedBy, documentIds));
        tenderReviewData.setDocuments(documents);
        tenderReviewData.setBlocks(blocks);
        tenderReviewData.setFields(fields);
        tenderReviewData.setCompareScopes(buildCompareScopes(documentIds));
        tenderReviewData.setExtractionMeta(extractionMeta);

        return tenderReviewData;
    }

    /**
     * LLM 润色审查结果。
     *
     * <p>将工具执行结果转换为 LLM 可理解的格式，
     * 让 LLM 生成更人性化的最终回复。</p>
     *
     * @param toolResult 工具执行结果
     * @param context    Agent 上下文
     * @return 润色后的回复
     */
    private String polishResult(ReviewTenderToolResult toolResult, AgentChatContext context) {
        log.info("[TenderReviewToolOrchestrator] 开始用LLM润色审查结果, riskLevel={}",
                toolResult.riskLevel());

        String systemPrompt = buildPolishSystemPrompt(toolResult);
        String userMessage = buildPolishUserMessage(toolResult, context.getQuery());

        try {
            LlmResponse llmResponse = llmService.chat(LlmRequest.builder()
                    .systemPrompt(systemPrompt)
                    .messages(List.of(LlmRequest.ChatMessage.builder()
                            .role("user")
                            .content(userMessage)
                            .build()))
                    .sessionId(context.getSessionId())
                    .temperature(0.7f)
                    .build());

            if (Boolean.TRUE.equals(llmResponse.getSuccess())) {
                return llmResponse.getContent();
            } else {
                log.warn("[TenderReviewToolOrchestrator] LLM润色失败，回退到原始结果: {}",
                        llmResponse.getErrorMessage());
                return toolResult.report() != null ? toolResult.report().getMarkdownContent() : toolResult.summary();
            }
        } catch (Exception e) {
            log.error("[TenderReviewToolOrchestrator] LLM润色异常，回退到原始结果", e);
            return toolResult.report() != null ? toolResult.report().getMarkdownContent() : toolResult.summary();
        }
    }

    /**
     * 构建润色用的系统提示词。
     */
    private String buildPolishSystemPrompt(ReviewTenderToolResult toolResult) {
        return """
                你是一个专业的标书审查助手，负责将标书审查结果转化为清晰、易懂的回复。

                审查结果概要：
                - 风险等级：%s
                - 风险分数：%d
                - 有效命中规则数：%d
                - 误报豁免数：%d

                请根据上述审查结果，用专业但易懂的语言向用户解释：
                1. 标书是否存在风险，如果存在，主要风险点是什么
                2. 建议后续如何处理
                3. 需要人工重点关注的地方

                回复要求：
                - 语言简洁专业，避免过于技术化的术语
                - 如果风险较高，要明确指出并给出建议
                - 如果风险较低，可以适当安抚并说明通过原因
                - 字数控制在 200-500 字之间
                """.formatted(
                toolResult.riskLevel(),
                toolResult.score() != null ? toolResult.score() : 0,
                toolResult.steps() != null ? toolResult.steps().size() : 0,
                0 // exemptionCount from steps if available
        );
    }

    /**
     * 构建润色用的用户消息。
     */
    private String buildPolishUserMessage(ReviewTenderToolResult toolResult, String originalQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户原始问题：").append(originalQuery != null ? originalQuery : "标书审查").append("\n\n");

        if (toolResult.report() != null && toolResult.report().getMarkdownContent() != null) {
            sb.append("详细审查报告：\n").append(toolResult.report().getMarkdownContent());
        } else {
            sb.append("审查摘要：").append(toolResult.summary());
        }

        return sb.toString();
    }

    /**
     * 构建 TenderCase 对象。
     */
    private com.liang.drugagent.scene.tender_review.model.TenderCase buildTenderCase(
            String caseId, String submittedBy, List<String> documentIds) {
        com.liang.drugagent.scene.tender_review.model.TenderCase tenderCase =
                new com.liang.drugagent.scene.tender_review.model.TenderCase();
        tenderCase.setCaseId(caseId);
        tenderCase.setScene("tender_review");
        tenderCase.setStatus("PARSED");
        tenderCase.setSubmittedBy(submittedBy);
        tenderCase.setCreatedAt(java.time.Instant.now());
        tenderCase.setDocumentIds(documentIds);
        return tenderCase;
    }

    /**
     * 构建 TenderDocument 对象。
     */
    private com.liang.drugagent.scene.tender_review.model.TenderDocument buildTenderDocument(
            String caseId, String docId, String filename) {
        com.liang.drugagent.scene.tender_review.model.TenderDocument document =
                new com.liang.drugagent.scene.tender_review.model.TenderDocument();
        document.setCaseId(caseId);
        document.setDocumentId(docId);
        document.setFilename(filename);
        document.setDocumentName(filename);
        document.setStatus("PARSED");
        document.setFileType(resolveFileType(filename));
        return document;
    }

    /**
     * 构建文档比对范围。
     */
    private List<com.liang.drugagent.scene.tender_review.model.CompareScope> buildCompareScopes(
            List<String> documentIds) {
        if (documentIds == null || documentIds.size() < 2) {
            return List.of();
        }
        com.liang.drugagent.scene.tender_review.model.CompareScope compareScope =
                new com.liang.drugagent.scene.tender_review.model.CompareScope();
        compareScope.setScopeId("CMP-" + UUID.randomUUID());
        compareScope.setScopeType("full_bid_compare");
        compareScope.setDocumentIds(documentIds);
        return List.of(compareScope);
    }

    /**
     * 根据文件名推断文件类型。
     */
    private String resolveFileType(String filename) {
        if (filename == null) {
            return "unknown";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".docx")) {
            return "docx";
        }
        if (lower.endsWith(".doc")) {
            return "doc";
        }
        if (lower.endsWith(".md")) {
            return "md";
        }
        return "unknown";
    }

    // ==================== 编排结果内部类 ====================

    /**
     * 编排结果。
     *
     * <p>包含完整的编排执行结果，供 AgentChatService 构建最终响应。</p>
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class OrchestrationResult {
        /**
         * 执行是否成功。
         */
        private boolean success;

        /**
         * 错误信息（如果失败）。
         */
        private String errorMessage;

        /**
         * 工具原始执行结果。
         */
        private ReviewTenderToolResult rawResult;

        /**
         * LLM 润色后的最终回复。
         */
        private String polishedAnswer;

        /**
         * 关联的案例 ID。
         */
        private String caseId;

        /**
         * 风险等级。
         */
        private String riskLevel;

        /**
         * 风险分数。
         */
        private Integer score;

        /**
         * 结构化报告。
         */
        private com.liang.drugagent.shared.domain.model.ReviewReport report;

        /**
         * 证据列表。
         */
        private List<com.liang.drugagent.shared.domain.model.EvidenceItem> evidenceList;

        /**
         * 执行步骤。
         */
        private List<String> steps;

        /**
         * 执行耗时（毫秒）。
         */
        private Long executionTimeMs;

        /**
         * 创建失败结果。
         */
        public static OrchestrationResult failure(String errorMessage) {
            return OrchestrationResult.builder()
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();
        }

        /**
         * 判断是否需要降级处理。
         */
        public boolean needsFallback() {
            return !success;
        }
    }
}
