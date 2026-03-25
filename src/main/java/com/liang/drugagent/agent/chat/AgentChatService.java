package com.liang.drugagent.agent.chat;

import com.liang.drugagent.agent.prompt.AgentPrompt;
import com.liang.drugagent.agent.route.AgentRouteService;
import com.liang.drugagent.controller.domain.request.agent.DrugAgentReq;
import com.liang.drugagent.controller.domain.response.agent.DrugAgentResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.common.MessageTypeEnum;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.scene.SceneWorkflow;
import com.liang.drugagent.scene.common.service.ChatMemoryService;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderCase;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.model.ExtractionMeta;
import com.liang.drugagent.scene.tender_review.model.CompareScope;
import com.liang.drugagent.scene.tender_review.service.TenderCaseService;
import com.liang.drugagent.scene.tender_review.service.TenderDocumentParseService;
import com.liang.drugagent.controller.domain.request.tender_review.TenderCaseCreateReq;
import com.liang.drugagent.scene.common.entity.ChatSession;
import com.liang.drugagent.shared.domain.model.WorkflowRouteDecision;
import com.liang.drugagent.shared.domain.model.WorkflowResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 主服务。
 *
 * <p>核心职责包括三方面：</p>
 * <ul>
 *   <li><b>对话处理</b>：同步/流式对话、文件上传对话</li>
 *   <li><b>意图路由</b>：通过 {@link AgentRouteService} 识别用户意图并路由到对应场景</li>
 *   <li><b>工作流执行</b>：根据路由结果调用对应的 {@link SceneWorkflow} 执行具体业务</li>
 * </ul>
 *
 * <p>整体流程：请求接入 → 意图识别 → 场景路由 → 工作流执行 → 响应构建</p>
 *
 * @author liangjiajian
 * @see AgentRouteService
 * @see WorkflowRegistryService
 * @see SceneWorkflow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final AgentRouteService agentRouteService;
    private final WorkflowRegistryService workflowRegistryService;
    private final LlmService llmService;
    private final ChatMemoryService chatMemoryService;
    private final TenderCaseService tenderCaseService;
    private final TenderDocumentParseService tenderDocumentParseService;

    // ==================== 对话入口 ====================

    /**
     * 处理同步对话请求。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>构建 {@link AgentChatContext} 上下文</li>
     *   <li>调用路由服务识别意图和场景</li>
     *   <li>若需要澄清，返回澄清问题</li>
     *   <li>否则执行对应工作流并返回结果</li>
     * </ol>
     *
     * <p>异常处理：任何路由或工作流执行异常都会触发降级处理。</p>
     *
     * @param req 对话请求，包含 query、sessionId、userId 等
     * @return AI 响应结果
     */
    public DrugAgentResp handleChat(DrugAgentReq req) {
        log.info("[AgentChatService] Start sync handling: sessionId={}, queryLength={}",
                req.getSessionId(), req.getQuery() == null ? 0 : req.getQuery().length());

        AgentChatContext context = AgentChatContext.from(req);

        try {
            // 1. 意图路由
            WorkflowRouteDecision decision = agentRouteService.route(req, context);

            // 2. 识别到特定场景，执行对应工作流
            if (decision.getScene() != SceneEnum.UNKNOWN) {
                return executeWorkflow(context, decision);
            }

            // 3. 未识别到特定场景，走通用对话
            return handleGeneralChat(context, decision);

        } catch (AgentRouteService.RouteException e) {
            log.error("[AgentChatService] Route failed, falling back: {}", e.getMessage());
            return handleFallback(req, context, e.getMessage());
        } catch (Exception e) {
            log.error("[AgentChatService] Unexpected error", e);
            return handleFallback(req, context, e.getMessage());
        }
    }

    /**
     * 处理流式对话请求（SSE）。
     *
     * <p>通过 Server-Sent Events 向客户端推送实时响应。</p>
     *
     * <p>推送事件序列：</p>
     * <ul>
     *   <li>meta - 路由元信息（traceId、scene、confidence 等）</li>
     *   <li>delta - 澄清问题内容（如需澄清）</li>
     *   <li>done - 结束标记</li>
     *   <li>error - 错误信息（异常时）</li>
     * </ul>
     *
     * @param req 对话请求
     * @return SseEmitter 用于推送事件流
     */
    public SseEmitter handleStreamChat(DrugAgentReq req) {
        log.info("[AgentChatService] Start stream handling: sessionId={}", req.getSessionId());

        AgentChatContext context = AgentChatContext.from(req);
        SseEmitter emitter = new SseEmitter(0L);

        try {
            // 1. 意图路由
            WorkflowRouteDecision decision = agentRouteService.route(req, context);

            // 2. 发送路由元信息
            sendEvent(emitter, "meta", Map.of(
                    "traceId", context.getTraceId(),
                    "scene", decision.getScene().name(),
                    "source", decision.getSource(),
                    "confidence", decision.getConfidence(),
                    "routeReason", decision.getReason() != null ? decision.getReason() : ""
            ));

            // 3. 识别到特定场景，执行工作流
            if (decision.getScene() != SceneEnum.UNKNOWN) {
                sendEvent(emitter, "workflow_start", Map.of(
                        "scene", decision.getScene().name(),
                        "message", "场景已识别，开始执行工作流..."
                ));
                sendEvent(emitter, "done", Map.of(
                        "scene", decision.getScene().name(),
                        "requiresStreamContinue", true
                ));
                emitter.complete();
                return emitter;
            }

            // 4. 未识别到特定场景，发送通用对话开始标记
            sendEvent(emitter, "workflow_start", Map.of(
                    "scene", "UNKNOWN",
                    "message", "开始通用对话..."
            ));
            sendEvent(emitter, "done", Map.of(
                    "scene", "UNKNOWN",
                    "requiresStreamContinue", true
            ));
            emitter.complete();
            return emitter;

        } catch (AgentRouteService.RouteException e) {
            log.error("[AgentChatService] Stream route failed: {}", e.getMessage());
            sendErrorEvent(emitter, "意图理解失败: " + e.getMessage());
            emitter.completeWithError(e);
        } catch (Exception e) {
            log.error("[AgentChatService] Stream unexpected error", e);
            sendErrorEvent(emitter, e.getMessage());
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 处理带文件上传的对话请求。
     *
     * <p>针对标书审查场景，上传文件后会：</p>
     * <ol>
     *   <li>创建或获取会话</li>
     *   <li>保存用户消息（包含文件列表）</li>
     *   <li>解析文档并构建审查数据</li>
     *   <li>执行标书审查工作流</li>
     *   <li>保存 AI 响应到会话</li>
     * </ol>
     *
     * @param query       用户输入的查询/指令
     * @param sceneHint   场景提示（如 "tender_review"）
     * @param sessionId   会话ID（可为空）
     * @param userId      用户ID
     * @param submittedBy 提交人
     * @param files       上传的文件列表
     * @return AI 响应结果
     * @throws IllegalArgumentException 文件列表为空时抛出
     */
    public DrugAgentResp handleFileUpload(String query,
                                         String sceneHint,
                                         String sessionId,
                                         String userId,
                                         String submittedBy,
                                         MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请至少上传一个文件");
        }

        // 1. 创建或获取会话
        ChatSession chatSession = getOrCreateSession(sessionId, sceneHint, userId);
        sessionId = chatSession.getId();

        // 2. 保存用户消息（包含文件信息）
        String fileNamesJson = buildFileNamesJson(files);
        chatMemoryService.addMessage(sessionId, "user", query, fileNamesJson);

        // 3. 构建请求并路由
        DrugAgentReq req = buildDrugAgentReq(query, sceneHint, sessionId, userId, files);
        AgentChatContext context = AgentChatContext.from(req);

        try {
            WorkflowRouteDecision decision = agentRouteService.route(req, context);

            // 4. 识别到特定场景，执行对应工作流
            if (decision.getScene() != SceneEnum.UNKNOWN) {
                // 若是标书审查场景，填充审查数据
                if (decision.getScene() == SceneEnum.TENDER_REVIEW) {
                    hydrateTenderMetadata(req, submittedBy, files);
                }
                DrugAgentResp resp = executeWorkflow(context, decision);
                String messageType = determineMessageType(resp);
                chatMemoryService.addMessage(sessionId, "assistant",
                        resp.getAnswer() != null ? resp.getAnswer() : resp.getSummary(), null, messageType);
                updateSessionTitle(sessionId, query, chatSession);
                resp.setSessionId(sessionId);
                return resp;
            }

            // 5. 未识别到特定场景，走通用对话
            DrugAgentResp resp = handleGeneralChat(context, decision);
            String messageType = determineMessageType(resp);
            chatMemoryService.addMessage(sessionId, "assistant",
                    resp.getAnswer() != null ? resp.getAnswer() : resp.getSummary(), null, messageType);
            updateSessionTitle(sessionId, query, chatSession);
            resp.setSessionId(sessionId);
            return resp;

        } catch (AgentRouteService.RouteException e) {
            log.error("[AgentChatService] Route failed for file upload: {}", e.getMessage());
            return handleFallback(req, context, e.getMessage());
        }
    }

    // ==================== 工作流执行 ====================

    /**
     * 执行工作流。
     *
     * <p>根据路由决策获取对应的工作流并执行。</p>
     *
     * <p>执行后记录日志，包含：</p>
     * <ul>
     *   <li>场景名称</li>
     *   <li>风险等级</li>
     *   <li>执行步骤数</li>
     * </ul>
     *
     * @param context  Agent 上下文
     * @param decision 路由决策结果
     * @return AI 响应结果
     */
    private DrugAgentResp executeWorkflow(AgentChatContext context, WorkflowRouteDecision decision) {
        SceneWorkflow workflow = workflowRegistryService.get(decision.getScene());

        // 空检查：防止未注册场景导致空指针
        if (workflow == null) {
            log.error("[AgentChatService] No workflow found for scene: {}", decision.getScene());
            return buildErrorResponse(context, decision, "暂不支持该场景: " + decision.getScene());
        }

        // 执行工作流
        WorkflowResult result = workflow.execute(context);

        log.info("[AgentChatService] Workflow executed: scene={}, riskLevel={}, stepCount={}",
                result.getScene(),
                result.getRiskLevel(),
                result.getSteps() == null ? 0 : result.getSteps().size());

        return buildResponse(context, result, decision);
    }

    /**
     * 处理通用对话。
     *
     * <p>当未识别到特定业务场景时，调用 LLM 进行通用对话。</p>
     *
     * @param context  Agent 上下文
     * @param decision 路由决策结果
     * @return AI 响应结果
     */
    private DrugAgentResp handleGeneralChat(AgentChatContext context, WorkflowRouteDecision decision) {
        log.info("[AgentChatService] 通用对话模式: query={}", context.getQuery());

        String answer;
        try {
            answer = llmService.chat(context.getQuery(), AgentPrompt.GENERAL_CHAT, context.getSessionId());
        } catch (Exception e) {
            log.error("[AgentChatService] 通用对话失败", e);
            answer = "抱歉，系统暂时无法处理您的请求，请稍后再试。";
        }

        DrugAgentResp resp = new DrugAgentResp();
        resp.setTraceId(context.getTraceId());
        resp.setScene(SceneEnum.UNKNOWN.name());
        resp.setRouteReason(decision != null ? decision.getReason() : "未识别到特定场景");
        resp.setRouteSource("general_chat");
        resp.setConfidence(decision != null ? decision.getConfidence() : 0.0);
        resp.setAnswer(answer);
        resp.setSummary("通用对话");
        return resp;
    }

    /**
     * 构建需要澄清的响应。
     *
     * @param context  Agent 上下文
     * @param result   工作流执行结果
     * @param decision 路由决策结果
     * @return 完整响应
     */
    private DrugAgentResp buildResponse(AgentChatContext context, WorkflowResult result,
                                        WorkflowRouteDecision decision) {
        DrugAgentResp resp = new DrugAgentResp();
        resp.setTraceId(context.getTraceId());
        resp.setScene(result.getScene().name());
        resp.setRouteReason(decision.getReason());
        resp.setRouteSource(decision.getSource());
        resp.setConfidence(decision.getConfidence());
        resp.setSummary(buildSummary(result));
        resp.setAnswer(result.getAnswer());
        resp.setRiskLevel(result.getRiskLevel());
        resp.setScore(result.getScore() != null ? result.getScore() : 0);
        resp.setSteps(result.getSteps() != null ? result.getSteps() : List.of());
        resp.setReport(result.getReport());
        resp.setEvidenceList(result.getEvidenceList());
        resp.setEvidenceGroups(result.getEvidenceGroups());
        return resp;
    }

    /**
     * 构建错误响应。
     *
     * @param context  Agent 上下文
     * @param decision 路由决策结果（可为 null）
     * @param message  错误信息
     * @return 错误响应
     */
    private DrugAgentResp buildErrorResponse(AgentChatContext context, WorkflowRouteDecision decision, String message) {
        DrugAgentResp resp = new DrugAgentResp();
        resp.setTraceId(context.getTraceId());
        resp.setScene(decision != null ? decision.getScene().name() : SceneEnum.UNKNOWN.name());
        resp.setRouteReason(message);
        resp.setRouteSource(decision != null ? decision.getSource() : "unknown");
        resp.setConfidence(decision != null ? decision.getConfidence() : 0.0);
        resp.setAnswer("抱歉，暂不支持该场景，请稍后再试。");
        resp.setSummary("场景不支持");
        return resp;
    }

    /**
     * 降级处理。
     *
     * <p>当路由或工作流执行异常时，尝试重新路由：</p>
     * <ol>
     *   <li>再次调用路由服务</li>
     *   <li>若路由到非 UNKNOWN 场景，执行对应工作流</li>
     *   <li>否则走通用对话</li>
     * </ol>
     *
     * @param req      原始请求
     * @param context  Agent 上下文
     * @param errorMsg 错误信息
     * @return 降级响应
     */
    private DrugAgentResp handleFallback(DrugAgentReq req, AgentChatContext context, String errorMsg) {
        log.warn("[AgentChatService] 降级处理: {}", errorMsg);

        try {
            // 尝试重新路由
            WorkflowRouteDecision decision = agentRouteService.route(req, context);
            if (decision.getScene() != SceneEnum.UNKNOWN) {
                context.setSceneType(decision.getScene());
                return executeWorkflow(context, decision);
            }
            // UNKNOWN 场景走通用对话
            return handleGeneralChat(context, decision);
        } catch (Exception e) {
            log.error("[AgentChatService] 降级路由也失败", e);
        }

        // 降级失败，返回友好提示
        DrugAgentResp resp = new DrugAgentResp();
        resp.setTraceId(context.getTraceId());
        resp.setScene(SceneEnum.UNKNOWN.name());
        resp.setRouteReason("降级处理失败: " + errorMsg);
        resp.setRouteSource("fallback");
        resp.setConfidence(0.0);
        resp.setAnswer("抱歉，系统暂时无法处理您的请求，请稍后再试。");
        resp.setSummary("系统暂时无法处理");
        return resp;
    }

    // ==================== 文件处理辅助 ====================

    /**
     * 获取或创建会话。
     *
     * @param sessionId 现有会话ID（可为空）
     * @param sceneHint 场景提示
     * @param userId    用户ID
     * @return 存在的会话或新建的会话
     */
    private ChatSession getOrCreateSession(String sessionId, String sceneHint, String userId) {
        if (sessionId != null && !sessionId.isBlank()) {
            ChatSession session = chatMemoryService.getById(sessionId);
            if (session != null) {
                return session;
            }
        }
        return chatMemoryService.createSession("新对话", sceneHint, userId);
    }

    /**
     * 构建文件名的 JSON 数组字符串。
     *
     * @param files 文件列表
     * @return JSON 格式的文件名数组，如 ["file1.doc","file2.doc"]
     */
    private String buildFileNamesJson(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return null;
        }
        return "[\"" + Arrays.stream(files)
                .map(f -> Optional.ofNullable(f.getOriginalFilename()).orElse("unnamed"))
                .collect(Collectors.joining("\",\"")) + "\"]";
    }

    /**
     * 构建发送给 Agent 的请求对象。
     *
     * @param query     用户查询
     * @param sceneHint 场景提示
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param files     上传的文件
     * @return DrugAgentReq 请求对象
     */
    private DrugAgentReq buildDrugAgentReq(String query, String sceneHint, String sessionId,
                                           String userId, MultipartFile[] files) {
        DrugAgentReq req = new DrugAgentReq();
        req.setQuery(query);
        req.setSceneHint(sceneHint);
        req.setSessionId(sessionId);
        req.setUserId(userId);

        // 生成文件ID列表和文件信息
        List<String> fileIds = new ArrayList<>();
        List<Map<String, Object>> uploadedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            String generatedId = UUID.randomUUID().toString();
            fileIds.add(generatedId);
            uploadedFiles.add(Map.of(
                    "fileId", generatedId,
                    "filename", Optional.ofNullable(file.getOriginalFilename()).orElse("unnamed"),
                    "size", file.getSize()
            ));
        }
        req.setFileIds(fileIds);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("uploadedFiles", uploadedFiles);
        req.setMetadata(metadata);
        return req;
    }

    /**
     * 填充标书审查元数据。
     *
     * <p>包含以下步骤：</p>
     * <ol>
     *   <li>创建标书审查 Case</li>
     *   <li>解析每个上传的文档</li>
     *   <li>构建 TenderReviewData 并存入请求元数据</li>
     * </ol>
     *
     * @param req         请求对象（用于存入 metadata）
     * @param submittedBy 提交人
     * @param files       上传的文档文件
     */
    private void hydrateTenderMetadata(DrugAgentReq req, String submittedBy, MultipartFile[] files) {
        // 收集文件名
        List<String> filenames = new ArrayList<>();
        for (MultipartFile file : files) {
            filenames.add(Optional.ofNullable(file.getOriginalFilename()).orElse("unnamed"));
        }

        // 创建标书审查 Case
        var caseResp = tenderCaseService.createCase(
                TenderCaseCreateReq.builder()
                        .filenames(filenames)
                        .submittedBy(submittedBy)
                        .build());

        // 初始化数据结构
        List<TenderDocument> documents = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Block> blocks = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Field> fields = new ArrayList<>();
        ExtractionMeta extractionMeta = new ExtractionMeta();
        extractionMeta.setSchemaVersion("tender-review-struct-v1");
        extractionMeta.setParserVersion("agent-upload-v1");
        extractionMeta.setParseSuccess(Boolean.TRUE);

        // 解析每个文档
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            String docId = caseResp.getDocumentIds().get(i);
            try {
                byte[] bytes = file.getBytes();
                // 存储文件内容
                tenderCaseService.storeFileContent(docId, bytes);
                // 解析文档
                var parseResult = tenderDocumentParseService.parseDocument(
                        docId,
                        filenames.get(i),
                        new ByteArrayInputStream(bytes)
                );
                // 收集解析结果
                documents.add(buildTenderDocument(caseResp.getCaseId(), docId, filenames.get(i)));
                blocks.addAll(Optional.ofNullable(parseResult.getParagraphBlocks()).orElse(List.of()));
                blocks.addAll(Optional.ofNullable(parseResult.getTableBlocks()).orElse(List.of()));
                fields.addAll(Optional.ofNullable(parseResult.getFields()).orElse(List.of()));
                if (Boolean.FALSE.equals(parseResult.getParseSuccess())) {
                    extractionMeta.setParseSuccess(Boolean.FALSE);
                }
            } catch (IOException e) {
                throw new IllegalStateException("文件处理失败: " + filenames.get(i), e);
            }
        }

        // 构建审查数据
        TenderReviewData data = new TenderReviewData();
        data.setACase(buildTenderCase(caseResp.getCaseId(), submittedBy, caseResp.getDocumentIds()));
        data.setDocuments(documents);
        data.setBlocks(blocks);
        data.setFields(fields);
        data.setCompareScopes(buildCompareScopes(caseResp.getDocumentIds()));
        data.setExtractionMeta(extractionMeta);

        // 存入元数据
        Map<String, Object> metadata = new HashMap<>(Optional.ofNullable(req.getMetadata()).orElse(Map.of()));
        metadata.put("caseId", caseResp.getCaseId());
        metadata.put("tenderReviewData", data);
        req.setMetadata(metadata);
    }

    /**
     * 构建 TenderCase 对象。
     */
    private TenderCase buildTenderCase(String caseId, String submittedBy, List<String> documentIds) {
        TenderCase tenderCase = new TenderCase();
        tenderCase.setCaseId(caseId);
        tenderCase.setScene("tender_review");
        tenderCase.setStatus("PARSED");
        tenderCase.setSubmittedBy(submittedBy);
        tenderCase.setCreatedAt(Instant.now());
        tenderCase.setDocumentIds(documentIds);
        return tenderCase;
    }

    /**
     * 构建 TenderDocument 对象。
     */
    private TenderDocument buildTenderDocument(String caseId, String docId, String filename) {
        TenderDocument document = new TenderDocument();
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
    private List<CompareScope> buildCompareScopes(List<String> documentIds) {
        if (documentIds == null || documentIds.size() < 2) {
            return List.of();
        }
        CompareScope compareScope = new CompareScope();
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

    /**
     * 从工作流结果构建摘要。
     *
     * <p>优先使用报告中的 overview.summary，其次使用 answer。</p>
     */
    private String buildSummary(WorkflowResult result) {
        if (result.getReport() != null
                && result.getReport().getOverview() != null
                && result.getReport().getOverview().getSummary() != null) {
            return result.getReport().getOverview().getSummary();
        }
        return result.getAnswer();
    }

    /**
     * 根据响应结果确定消息类型。
     *
     * <p>判断逻辑：
     * <ul>
     *   <li>如果 requiresClarification=true → assistant_clarify</li>
     *   <li>如果有结构化报告 → assistant_result_card</li>
     *   <li>否则 → assistant_text</li>
     * </ul>
     *
     * @param resp AI 响应结果
     * @return 消息类型代码
     */
    private String determineMessageType(DrugAgentResp resp) {
        if (resp == null) {
            return MessageTypeEnum.ASSISTANT_TEXT.getCode();
        }
        if (resp.isRequiresClarification()) {
            return MessageTypeEnum.ASSISTANT_CLARIFY.getCode();
        }
        if (resp.getReport() != null) {
            return MessageTypeEnum.ASSISTANT_RESULT_CARD.getCode();
        }
        return MessageTypeEnum.ASSISTANT_TEXT.getCode();
    }

    /**
     * 更新会话标题。
     *
     * <p>仅当会话标题为默认的"新对话"时才更新，标题取自用户输入的前20个字符。</p>
     */
    private void updateSessionTitle(String sessionId, String query, ChatSession chatSession) {
        if (query != null && !query.isBlank() && chatSession.getTitle().equals("新对话")) {
            String title = query.length() > 20 ? query.substring(0, 20) + "..." : query;
            chatMemoryService.updateSessionTitle(sessionId, title);
        }
    }

    /**
     * 发送 SSE 事件。
     *
     * @param emitter  SseEmitter
     * @param eventName 事件名称
     * @param data     事件数据
     * @throws IOException 发送失败时抛出
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    /**
     * 发送错误事件（带异常捕获）。
     */
    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            sendEvent(emitter, "error", message);
        } catch (IOException ignored) {
            // 忽略发送失败
        }
    }
}
