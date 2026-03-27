package com.liang.drugagent.scene.tender_review.tool;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.tool.dto.ReviewTenderToolReq;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.service.TenderCaseService;
import com.liang.drugagent.scene.tender_review.service.TenderDocumentParseService;
import com.liang.drugagent.scene.tender_review.workflow.TenderReviewWorkflow;
import com.liang.drugagent.shared.domain.model.WorkflowResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 标书审查工具类。
 *
 * <p>单职责工具类，负责执行标书审查的具体业务逻辑。
 * 内部调用 {@link TenderReviewWorkflow} 完成完整的审查流程。</p>
 *
 * <p>设计原则：
 * <ul>
 *   <li>单职责：仅负责标书审查，不涉及 LLM 调用和编排</li>
 *   <li>可测试：输入输出都是结构化对象，便于单元测试</li>
 *   <li>可复用：可以被 orchestrator 或其他组件独立调用</li>
 * </ul>
 *
 * <p>该工具作为 LLM Tool Calling 的执行载体，
 * 由 {@link TenderReviewToolOrchestrator} 负责调用和管理。</p>
 *
 * @author liangjiajian
 * @see TenderReviewToolOrchestrator
 * @see TenderReviewWorkflow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewTenderTool {

    public static final String TOOL_NAME = "review_tender";
    public static final String TOOL_DESCRIPTION = """
            标书审查工具。当用户需要审查标书文件、识别围标串标风险、分析技术方案雷同等场景时调用此工具。

            输入参数：
            - sessionId: 关联会话ID
            - fileIds: 待审查文件ID列表
            - reviewFocus: 审查重点（围标风险/技术方案雷同/商务条款）
            - userInstruction: 用户额外补充说明
            - needStructuredReport: 是否需要完整报告

            返回结果包含：
            - success: 是否成功
            - caseId: 案例ID
            - summary: 摘要
            - riskLevel: 风险等级（high/medium/low）
            - score: 风险分
            - steps: 执行步骤
            - report: 结构化报告
            - evidenceList: 证据列表
            - message: 错误/补充说明
            """;

    private final TenderReviewWorkflow tenderReviewWorkflow;
    private final TenderDocumentParseService parseService;
    private final TenderCaseService caseService;

    /**
     * 执行标书审查。
     *
     * <p>完整流程：
     * <ol>
     *   <li>参数校验</li>
     *   <li>根据 sessionId/fileIds 获取文件</li>
     *   <li>组装 TenderReviewData</li>
     *   <li>调用 TenderReviewWorkflow.execute()</li>
     *   <li>转换为 ReviewTenderToolResult 返回</li>
     * </ol>
     *
     * @param request 审查请求对象
     * @return 审查结果
     */
    public ReviewTenderToolResult reviewTender(ReviewTenderToolRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("开始标书审查请求");

        // 1. 参数校验
        validateRequest(request);

        try {
            // 2. 根据 sessionId/fileIds 获取文件
            log.info("获取待审查文件, fileIds=" + request.fileIds());
            List<TenderDocument> documents = fetchDocuments(request);

            if (documents == null || documents.size() < 2) {
                log.warn("文件数量不足, count=" + (documents != null ? documents.size() : 0));
                return ReviewTenderToolResult.failure("至少需要 2 份标书文件进行比对审查，当前可用文件数量不足");
            }

            // 3. 组装 TenderReviewData
            log.info("组装标书审查数据");
            TenderReviewData reviewData = assembleTenderReviewData(request, documents);

            // 4. 调用 TenderReviewWorkflow.execute()
            log.info("执行标书审查工作流");
            AgentChatContext context = buildAgentChatContext(request, reviewData);
            WorkflowResult workflowResult = tenderReviewWorkflow.execute(context);

            // 5. 转换为 ReviewTenderToolResult 返回
            long executionTimeMs = System.currentTimeMillis() - startTime;
            ReviewTenderToolResult result = mapToToolResult(workflowResult, request, executionTimeMs);

            log.info("标书审查完成, riskLevel={}, score={}, executionTimeMs={}",
                    result.riskLevel(), result.score(), executionTimeMs);

            return result;

        } catch (IllegalArgumentException e) {
            log.warn("参数校验失败: " + e.getMessage());
            return ReviewTenderToolResult.failure("参数校验失败: " + e.getMessage());
        } catch (IOException e) {
            log.error("文件读取失败", e);
            return ReviewTenderToolResult.failure("文件读取失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("标书审查执行失败", e);
            return ReviewTenderToolResult.failure("审查执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行标书审查（带预加载的 TenderReviewData）。
     *
     * <p>适用于 Orchestrator 已经构建好 TenderReviewData 的场景。</p>
     *
     * @param request         工具请求对象
     * @param tenderReviewData 预加载的标书审查数据
     * @return 工具执行结果
     */
    public ReviewTenderToolResult reviewTender(ReviewTenderToolRequest request, TenderReviewData tenderReviewData) {
        log.info("[ReviewTenderTool] 开始使用预加载数据执行标书审查: sessionId={}",
                request.sessionId());

        if (request == null) {
            return ReviewTenderToolResult.failure("请求对象不能为空");
        }

        try {
            // 构建包含 TenderReviewData 的上下文
            AgentChatContext context = buildContextWithData(request, tenderReviewData);

            // 调用工作流执行审查
            WorkflowResult workflowResult = tenderReviewWorkflow.execute(context);

            return convertToToolResult(workflowResult);

        } catch (Exception e) {
            log.error("[ReviewTenderTool] 使用预加载数据执行审查失败: {}", e.getMessage(), e);
            return ReviewTenderToolResult.failure("标书审查执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行标书审查（供外部调用的入口方法）。
     */
    public ReviewTenderToolResult execute(ReviewTenderToolReq request, TenderReviewData tenderReviewData) {
        ReviewTenderToolRequest convertedRequest = new ReviewTenderToolRequest(
                request.sessionId(),
                request.fileIds(),
                request.reviewFocus(),
                request.userInstruction(),
                request.needStructuredReport()
        );
        return reviewTender(convertedRequest, tenderReviewData);
    }

    /**
     * 获取工具名称。
     */
    public String getToolName() {
        return TOOL_NAME;
    }

    /**
     * 获取工具描述。
     */
    public String getToolDescription() {
        return TOOL_DESCRIPTION;
    }

    /**
     * 根据请求构建 Agent 上下文。
     */
    private AgentChatContext buildContext(ReviewTenderToolRequest request) {
        String query = buildQueryFromRequest(request);
        return AgentChatContext.fromToolRequest(
                request.sessionId(),
                query,
                request.fileIds(),
                null
        );
    }

    /**
     * 根据请求构建包含 TenderReviewData 的 Agent 上下文。
     */
    private AgentChatContext buildContextWithData(ReviewTenderToolRequest request, TenderReviewData tenderReviewData) {
        String query = buildQueryFromRequest(request);
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("tenderReviewData", tenderReviewData);
        metadata.put("reviewFocus", request.reviewFocus());
        metadata.put("userInstruction", request.userInstruction());
        metadata.put("needStructuredReport", request.needStructuredReport());
        return AgentChatContext.fromToolRequest(
                request.sessionId(),
                query,
                request.fileIds(),
                metadata
        );
    }

    /**
     * 根据请求构建查询文本。
     */
    private String buildQueryFromRequest(ReviewTenderToolRequest request) {
        StringBuilder query = new StringBuilder();
        query.append("标书审查请求");

        if (request.reviewFocus() != null && !request.reviewFocus().isBlank()) {
            query.append("，审查重点：").append(request.reviewFocus());
        }

        if (request.userInstruction() != null && !request.userInstruction().isBlank()) {
            query.append("，用户补充：").append(request.userInstruction());
        }

        if (Boolean.TRUE.equals(request.needStructuredReport())) {
            query.append("，需要完整报告");
        }

        return query.toString();
    }

    /**
     * 将 WorkflowResult 转换为 ReviewTenderToolResult。
     */
    private ReviewTenderToolResult convertToToolResult(WorkflowResult workflowResult) {
        if (workflowResult == null) {
            return ReviewTenderToolResult.failure("工作流执行返回为空");
        }

        String caseId = null;
        if (workflowResult.getReport() != null) {
            caseId = workflowResult.getReport().getCaseId();
        }

        String summary = buildSummary(workflowResult);

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
     * 构建摘要信息。
     */
    private String buildSummary(WorkflowResult result) {
        if (result.getReport() != null
                && result.getReport().getOverview() != null
                && result.getReport().getOverview().getSummary() != null) {
            return result.getReport().getOverview().getSummary();
        }
        return result.getAnswer();
    }

    // ==================== 辅助方法 ====================

    /**
     * 校验请求参数。
     *
     * @throws IllegalArgumentException 校验失败时抛出
     */
    private void validateRequest(ReviewTenderToolRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("审查请求不能为空");
        }
        request.validate();

        if (request.fileIds() == null || request.fileIds().isEmpty()) {
            throw new IllegalArgumentException("待审查文件ID列表不能为空");
        }
        if (request.fileIds().size() < 2) {
            throw new IllegalArgumentException("至少需要 2 份标书文件进行比对审查");
        }
    }

    /**
     * 根据 sessionId 和 fileIds 获取文档列表。
     *
     * <p>优先使用 fileIds 直接获取文件，
     * 如果 fileIds 中有文件不存在于 store 中，
     * 尝试通过 sessionId 从历史记录中获取。</p>
     *
     * @param request 审查请求
     * @return 文档列表
     * @throws IOException 文件读取失败时抛出
     */
    private List<TenderDocument> fetchDocuments(ReviewTenderToolRequest request) throws IOException {
        List<TenderDocument> documents = new ArrayList<>();
        Set<String> fetchedDocIds = new HashSet<>();

        // 优先通过 fileIds 直接获取
        for (String fileId : request.fileIds()) {
            if (fetchedDocIds.contains(fileId)) {
                continue;
            }
            Optional<TenderDocument> docOpt = caseService.getDocument(fileId);
            if (docOpt.isPresent()) {
                documents.add(docOpt.get());
                fetchedDocIds.add(fileId);
            } else {
                log.warn("[ReviewTenderTool] 文档未在存储中找到, fileId={}", fileId);
            }
        }

        // 如果通过 fileIds 获取到的文档不足，尝试通过 sessionId 获取更多文档
        if (documents.size() < 2 && request.sessionId() != null) {
            List<TenderDocument> sessionDocs = fetchDocumentsBySessionId(request.sessionId());
            for (TenderDocument doc : sessionDocs) {
                if (!fetchedDocIds.contains(doc.getDocumentId())) {
                    documents.add(doc);
                    fetchedDocIds.add(doc.getDocumentId());
                }
            }
        }

        return documents;
    }

    /**
     * 根据会话ID获取该会话关联的所有文档。
     *
     * @param sessionId 会话ID
     * @return 文档列表
     */
    private List<TenderDocument> fetchDocumentsBySessionId(String sessionId) {
        List<TenderDocument> sessionDocs = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.TenderCase> allCases = caseService.listCases();

        for (var tenderCase : allCases) {
            if (tenderCase.getDocumentIds() != null) {
                for (String docId : tenderCase.getDocumentIds()) {
                    Optional<TenderDocument> docOpt = caseService.getDocument(docId);
                    docOpt.ifPresent(sessionDocs::add);
                }
            }
        }

        return sessionDocs;
    }

    /**
     * 组装 TenderReviewData。
     *
     * <p>将文档列表解析为结构化的 TenderReviewData，
     * 包含文档元数据、文本块、提取字段等信息。</p>
     *
     * @param request   审查请求
     * @param documents 文档列表
     * @return 标书审查数据
     * @throws IOException 文件读取失败时抛出
     */
    private TenderReviewData assembleTenderReviewData(ReviewTenderToolRequest request,
                                                       List<TenderDocument> documents) throws IOException {
        TenderReviewData reviewData = new TenderReviewData();

        // 创建 Case 对象
        var tenderCase = new com.liang.drugagent.scene.tender_review.model.TenderCase();
        tenderCase.setCaseId(generateCaseId());
        tenderCase.setScene("tender_review");
        reviewData.setACase(tenderCase);

        List<com.liang.drugagent.scene.tender_review.model.TenderDocument> tenderDocuments = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Block> allBlocks = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Field> allFields = new ArrayList<>();

        for (TenderDocument doc : documents) {
            // 获取文件内容
            Optional<byte[]> contentOpt = caseService.getFileContent(doc.getDocumentId());
            if (contentOpt.isEmpty()) {
                log.warn("[ReviewTenderTool] 文件内容未找到, docId={}", doc.getDocumentId());
                continue;
            }

            byte[] content = contentOpt.get();
            String contentStr = new String(content, StandardCharsets.UTF_8);

            // 解析文档
            TenderDocumentParseResult parseResult = parseDocument(doc, contentStr);
            tenderDocuments.add(parseResult.document());
            allBlocks.addAll(parseResult.blocks());
            allFields.addAll(parseResult.fields());
        }

        reviewData.setDocuments(tenderDocuments);
        reviewData.setBlocks(allBlocks);
        reviewData.setFields(allFields);

        // 设置比对范围
        if (tenderDocuments.size() >= 2) {
            var compareScope = new com.liang.drugagent.scene.tender_review.model.CompareScope();
            compareScope.setScopeId("CMP-" + UUID.randomUUID());
            compareScope.setScopeType("full_bid_compare");
            compareScope.setDocumentIds(tenderDocuments.stream()
                    .map(com.liang.drugagent.scene.tender_review.model.TenderDocument::getDocumentId)
                    .toList());
            reviewData.setCompareScopes(List.of(compareScope));
        }

        // 设置提取元信息
        var extractionMeta = new com.liang.drugagent.scene.tender_review.model.ExtractionMeta();
        extractionMeta.setSchemaVersion("tender-review-v1");
        extractionMeta.setParserVersion("file-content-parser-v1");
        extractionMeta.setParseSuccess(Boolean.TRUE);
        reviewData.setExtractionMeta(extractionMeta);

        return reviewData;
    }

    /**
     * 解析文档内容。
     *
     * @param doc      文档元数据
     * @param content  文档文本内容
     * @return 解析结果（包含文档对象、块列表、字段列表）
     */
    private TenderDocumentParseResult parseDocument(TenderDocument doc, String content) {
        com.liang.drugagent.scene.tender_review.model.TenderDocument tenderDoc =
                new com.liang.drugagent.scene.tender_review.model.TenderDocument();
        tenderDoc.setDocumentId(doc.getDocumentId());
        tenderDoc.setCaseId(doc.getCaseId());
        tenderDoc.setDocumentName(doc.getDocumentName() != null ? doc.getDocumentName() : doc.getFilename());
        tenderDoc.setFilename(doc.getFilename());
        tenderDoc.setFileType(doc.getFileType());
        tenderDoc.setStatus("PARSED");

        List<com.liang.drugagent.scene.tender_review.model.Block> blocks = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Field> fields = new ArrayList<>();

        // 简单的文本块解析（按行分割）
        String[] lines = content.split("\n");
        String currentChapter = "全文";
        int paragraphNo = 0;

        StringBuilder paragraphBuffer = new StringBuilder();

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty()) {
                if (paragraphBuffer.length() > 0) {
                    paragraphNo++;
                    addParagraphBlock(tenderDoc.getDocumentId(), currentChapter, paragraphNo,
                            paragraphBuffer.toString(), blocks, fields);
                    paragraphBuffer.setLength(0);
                }
                continue;
            }

            // 检测标题行（以 # 开头）
            if (trimmedLine.startsWith("#")) {
                if (paragraphBuffer.length() > 0) {
                    paragraphNo++;
                    addParagraphBlock(tenderDoc.getDocumentId(), currentChapter, paragraphNo,
                            paragraphBuffer.toString(), blocks, fields);
                    paragraphBuffer.setLength(0);
                }
                currentChapter = trimmedLine.replaceAll("^#+\\s*", "");
                continue;
            }

            if (paragraphBuffer.length() > 0) {
                paragraphBuffer.append("\n");
            }
            paragraphBuffer.append(trimmedLine);
        }

        // 处理最后一段
        if (paragraphBuffer.length() > 0) {
            paragraphNo++;
            addParagraphBlock(tenderDoc.getDocumentId(), currentChapter, paragraphNo,
                    paragraphBuffer.toString(), blocks, fields);
        }

        return new TenderDocumentParseResult(tenderDoc, blocks, fields);
    }

    /**
     * 添加段落块。
     */
    private void addParagraphBlock(String documentId, String chapterPath, int paragraphNo,
                                    String content,
                                    List<com.liang.drugagent.scene.tender_review.model.Block> blocks,
                                    List<com.liang.drugagent.scene.tender_review.model.Field> fields) {
        if (content == null || content.isBlank()) {
            return;
        }

        com.liang.drugagent.scene.tender_review.model.Block block =
                new com.liang.drugagent.scene.tender_review.model.Block();
        block.setBlockId("BLK-" + UUID.randomUUID());
        block.setDocumentId(documentId);
        block.setBlockType("paragraph");
        block.setChapterPath(chapterPath);
        block.setContent(content);
        block.setRawContent(content);
        block.setAnchorParagraphNo(paragraphNo);
        blocks.add(block);

        // 添加文本片段字段
        com.liang.drugagent.scene.tender_review.model.Field field =
                new com.liang.drugagent.scene.tender_review.model.Field();
        field.setFieldId("FLD-" + UUID.randomUUID());
        field.setDocumentId(documentId);
        field.setBlockId(block.getBlockId());
        field.setFieldType("text_segment");
        field.setFieldName(chapterPath);
        field.setFieldValue(content);
        field.setNormalizedValue(content);
        field.setNormalizedKey(chapterPath.toLowerCase());
        field.setChapterPath(chapterPath);
        fields.add(field);
    }

    /**
     * 构建 AgentChatContext。
     *
     * @param request    审查请求
     * @param reviewData 标书审查数据
     * @return Agent 上下文
     */
    private AgentChatContext buildAgentChatContext(ReviewTenderToolRequest request, TenderReviewData reviewData) {
        String query = buildQueryFromRequest(request);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenderReviewData", reviewData);
        metadata.put("reviewFocus", request.reviewFocus());
        metadata.put("userInstruction", request.userInstruction());
        metadata.put("needStructuredReport", request.needStructuredReport());
        return AgentChatContext.fromToolRequest(
                request.sessionId(),
                query,
                request.fileIds(),
                metadata
        );
    }

    /**
     * 将 WorkflowResult 映射为 ReviewTenderToolResult。
     *
     * @param workflowResult 工作流执行结果
     * @param request        原始请求
     * @param executionTimeMs 执行耗时
     * @return 工具返回结果
     */
    private ReviewTenderToolResult mapToToolResult(WorkflowResult workflowResult,
                                                    ReviewTenderToolRequest request,
                                                    long executionTimeMs) {
        if (workflowResult == null) {
            return ReviewTenderToolResult.failure("工作流执行结果为空");
        }

        String caseId = null;
        if (workflowResult.getReport() != null) {
            caseId = workflowResult.getReport().getCaseId();
        }
        if (caseId == null) {
            caseId = generateCaseId();
        }

        String summary = buildSummary(workflowResult);
        List<String> steps = workflowResult.getSteps() != null
                ? workflowResult.getSteps()
                : List.of("参数校验", "文件获取", "数据组装", "工作流执行", "结果转换");

        return ReviewTenderToolResult.success(
                caseId,
                summary,
                workflowResult.getRiskLevel(),
                workflowResult.getScore(),
                steps,
                workflowResult.getReport(),
                workflowResult.getEvidenceList()
        );
    }

    /**
     * 生成案例ID。
     */
    private String generateCaseId() {
        return "CASE-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ==================== 内部类：文档解析结果 ====================

    /**
     * 文档解析结果。
     */
    private record TenderDocumentParseResult(
            com.liang.drugagent.scene.tender_review.model.TenderDocument document,
            List<com.liang.drugagent.scene.tender_review.model.Block> blocks,
            List<com.liang.drugagent.scene.tender_review.model.Field> fields
    ) {
    }
}
