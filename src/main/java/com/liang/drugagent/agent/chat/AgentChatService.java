package com.liang.drugagent.agent.chat;

import com.liang.drugagent.agent.route.AgentRouteService;
import com.liang.drugagent.controller.domain.request.agent.DrugAgentReq;
import com.liang.drugagent.controller.domain.response.agent.DrugAgentResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.SceneWorkflow;
import com.liang.drugagent.scene.common.service.ChatMessageService;
import com.liang.drugagent.scene.common.service.ChatSessionService;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderCase;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.model.ExtractionMeta;
import com.liang.drugagent.scene.tender_review.model.CompareScope;
import com.liang.drugagent.scene.tender_review.service.TenderCaseService;
import com.liang.drugagent.scene.tender_review.service.TenderDocumentParseService;
import com.liang.drugagent.controller.domain.request.tender_review.TenderCaseCreateReq;
import com.liang.drugagent.thirdparty.db.entity.ChatSession;
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
 * <p>核心职责：对话处理、意图路由、工作流执行。
 *
 * @author liangjiajian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final AgentRouteService agentRouteService;
    private final WorkflowRegistryService workflowRegistryService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final TenderCaseService tenderCaseService;
    private final TenderDocumentParseService tenderDocumentParseService;

    // ==================== 对话入口 ====================

    /** 处理同步对话请求。 */
    public DrugAgentResp handleChat(DrugAgentReq req) {
        log.info("[AgentChatService] Start sync handling: sessionId={}, queryLength={}",
                req.getSessionId(), req.getQuery() == null ? 0 : req.getQuery().length());

        AgentChatContext context = AgentChatContext.from(req);

        try {
            WorkflowRouteDecision decision = agentRouteService.route(req, context);

            if (agentRouteService.shouldClarify(decision)) {
                String question = agentRouteService.generateClarificationQuestion(decision);
                return buildClarificationResponse(context, decision, question);
            }

            return executeWorkflow(context, decision);

        } catch (AgentRouteService.RouteException e) {
            log.error("[AgentChatService] Route failed, falling back: {}", e.getMessage());
            return handleFallback(req, context, e.getMessage());
        } catch (Exception e) {
            log.error("[AgentChatService] Unexpected error", e);
            return handleFallback(req, context, e.getMessage());
        }
    }

    /** 处理流式对话请求。 */
    public SseEmitter handleStreamChat(DrugAgentReq req) {
        log.info("[AgentChatService] Start stream handling: sessionId={}", req.getSessionId());

        AgentChatContext context = AgentChatContext.from(req);
        SseEmitter emitter = new SseEmitter(0L);

        try {
            WorkflowRouteDecision decision = agentRouteService.route(req, context);

            // 发送元信息
            sendEvent(emitter, "meta", Map.of(
                    "traceId", context.getTraceId(),
                    "scene", decision.getScene().name(),
                    "source", decision.getSource(),
                    "confidence", decision.getConfidence(),
                    "routeReason", decision.getReason() != null ? decision.getReason() : ""
            ));

            if (agentRouteService.shouldClarify(decision)) {
                String question = agentRouteService.generateClarificationQuestion(decision);
                sendEvent(emitter, "delta", question);
                sendEvent(emitter, "done", Map.of(
                        "requiresClarification", true,
                        "scene", decision.getScene().name()
                ));
                emitter.complete();
                return emitter;
            }

            sendEvent(emitter, "workflow_start", Map.of(
                    "scene", decision.getScene().name(),
                    "message", "场景已识别，开始执行工作流..."
            ));

            sendEvent(emitter, "done", Map.of(
                    "scene", decision.getScene().name(),
                    "requiresStreamContinue", true
            ));
            emitter.complete();

        } catch (AgentRouteService.RouteException e) {
            log.error("[AgentChatService] Stream route failed: {}", e.getMessage());
            try {
                sendEvent(emitter, "error", "意图理解失败: " + e.getMessage());
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        } catch (Exception e) {
            log.error("[AgentChatService] Stream unexpected error", e);
            try {
                sendEvent(emitter, "error", e.getMessage());
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /** 处理带文件上传的对话请求。 */
    public DrugAgentResp handleFileUpload(String query,
                                         String sceneHint,
                                         String sessionId,
                                         String userId,
                                         String submittedBy,
                                         MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请至少上传一个文件");
        }

        ChatSession chatSession = getOrCreateSession(sessionId, sceneHint, userId);
        sessionId = chatSession.getId();

        String fileNamesJson = buildFileNamesJson(files);
        chatMessageService.addMessage(sessionId, "user", query, fileNamesJson);

        DrugAgentReq req = buildDrugAgentReq(query, sceneHint, sessionId, userId, files);

        AgentChatContext context = AgentChatContext.from(req);

        try {
            WorkflowRouteDecision decision = agentRouteService.route(req, context);

            if (decision.getScene() == SceneEnum.TENDER_REVIEW) {
                hydrateTenderMetadata(req, submittedBy, files);
            }

            DrugAgentResp resp = executeWorkflow(context, decision);

            chatMessageService.addMessage(sessionId, "assistant",
                    resp.getAnswer() != null ? resp.getAnswer() : resp.getSummary(), null);

            updateSessionTitle(sessionId, query, chatSession);
            resp.setSessionId(sessionId);

            return resp;

        } catch (AgentRouteService.RouteException e) {
            log.error("[AgentChatService] Route failed for file upload: {}", e.getMessage());
            return handleFallback(req, context, e.getMessage());
        }
    }

    // ==================== 工作流执行 ====================

    private DrugAgentResp executeWorkflow(AgentChatContext context, WorkflowRouteDecision decision) {
        SceneWorkflow workflow = workflowRegistryService.get(decision.getScene());
        WorkflowResult result = workflow.execute(context);

        log.info("[AgentChatService] Workflow executed: scene={}, riskLevel={}, stepCount={}",
                result.getScene(),
                result.getRiskLevel(),
                result.getSteps() == null ? 0 : result.getSteps().size());

        return buildResponse(context, result, decision);
    }

    private DrugAgentResp buildClarificationResponse(AgentChatContext context,
                                                     WorkflowRouteDecision decision,
                                                     String question) {
        DrugAgentResp resp = new DrugAgentResp();
        resp.setTraceId(context.getTraceId());
        resp.setScene(decision.getScene().name());
        resp.setRouteReason(decision.getReason());
        resp.setRouteSource(decision.getSource());
        resp.setConfidence(decision.getConfidence());
        resp.setRequiresClarification(true);
        resp.setClarificationQuestion(question);
        resp.setAnswer(question);
        resp.setSummary("需要补充信息");
        return resp;
    }

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

    private DrugAgentResp handleFallback(DrugAgentReq req, AgentChatContext context, String errorMsg) {
        log.warn("[AgentChatService] Falling back: {}", errorMsg);

        try {
            WorkflowRouteDecision decision = agentRouteService.route(req, context);
            if (decision.getScene() != SceneEnum.UNKNOWN) {
                context.setSceneType(decision.getScene());
                return executeWorkflow(context, decision);
            }
        } catch (Exception e) {
            log.error("[AgentChatService] Fallback route also failed", e);
        }

        DrugAgentResp resp = new DrugAgentResp();
        resp.setTraceId(context.getTraceId());
        resp.setScene(SceneEnum.UNKNOWN.name());
        resp.setRouteReason("LLM和规则都无法决策: " + errorMsg);
        resp.setRouteSource("fallback");
        resp.setConfidence(0.0);
        resp.setAnswer("抱歉，系统暂时无法理解您的请求，请稍后再试或提供更详细的信息。");
        resp.setSummary("系统无法理解请求");
        return resp;
    }

    // ==================== 文件处理辅助 ====================

    private ChatSession getOrCreateSession(String sessionId, String sceneHint, String userId) {
        if (sessionId != null && !sessionId.isBlank()) {
            ChatSession session = chatSessionService.getById(sessionId);
            if (session != null) {
                return session;
            }
        }
        return chatSessionService.createSession("新对话", sceneHint, userId);
    }

    private String buildFileNamesJson(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return null;
        }
        return "[\"" + Arrays.stream(files)
                .map(f -> Optional.ofNullable(f.getOriginalFilename()).orElse("unnamed"))
                .collect(Collectors.joining("\",\"")) + "\"]";
    }

    private DrugAgentReq buildDrugAgentReq(String query, String sceneHint, String sessionId,
                                           String userId, MultipartFile[] files) {
        DrugAgentReq req = new DrugAgentReq();
        req.setQuery(query);
        req.setSceneHint(sceneHint);
        req.setSessionId(sessionId);
        req.setUserId(userId);

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

    private void hydrateTenderMetadata(DrugAgentReq req, String submittedBy, MultipartFile[] files) {
        List<String> filenames = new ArrayList<>();
        for (MultipartFile file : files) {
            filenames.add(Optional.ofNullable(file.getOriginalFilename()).orElse("unnamed"));
        }

        var caseResp = tenderCaseService.createCase(
                TenderCaseCreateReq.builder()
                        .filenames(filenames)
                        .submittedBy(submittedBy)
                        .build());

        List<TenderDocument> documents = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Block> blocks = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Field> fields = new ArrayList<>();
        ExtractionMeta extractionMeta = new ExtractionMeta();
        extractionMeta.setSchemaVersion("tender-review-struct-v1");
        extractionMeta.setParserVersion("agent-upload-v1");
        extractionMeta.setParseSuccess(Boolean.TRUE);

        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            String docId = caseResp.getDocumentIds().get(i);
            try {
                byte[] bytes = file.getBytes();
                tenderCaseService.storeFileContent(docId, bytes);
                var parseResult = tenderDocumentParseService.parseDocument(
                        docId,
                        filenames.get(i),
                        new ByteArrayInputStream(bytes)
                );
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

        TenderReviewData data = new TenderReviewData();
        data.setACase(buildTenderCase(caseResp.getCaseId(), submittedBy, caseResp.getDocumentIds()));
        data.setDocuments(documents);
        data.setBlocks(blocks);
        data.setFields(fields);
        data.setCompareScopes(buildCompareScopes(caseResp.getDocumentIds()));
        data.setExtractionMeta(extractionMeta);

        Map<String, Object> metadata = new HashMap<>(Optional.ofNullable(req.getMetadata()).orElse(Map.of()));
        metadata.put("caseId", caseResp.getCaseId());
        metadata.put("tenderReviewData", data);
        req.setMetadata(metadata);
    }

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

    private String buildSummary(WorkflowResult result) {
        if (result.getReport() != null
                && result.getReport().getOverview() != null
                && result.getReport().getOverview().getSummary() != null) {
            return result.getReport().getOverview().getSummary();
        }
        return result.getAnswer();
    }

    private void updateSessionTitle(String sessionId, String query, ChatSession chatSession) {
        if (query != null && !query.isBlank() && chatSession.getTitle().equals("新对话")) {
            String title = query.length() > 20 ? query.substring(0, 20) + "..." : query;
            chatSessionService.updateSessionTitle(sessionId, title);
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }
}
