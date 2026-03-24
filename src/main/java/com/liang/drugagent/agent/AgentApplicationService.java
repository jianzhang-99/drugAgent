package com.liang.drugagent.agent;

import com.liang.drugagent.controller.domain.request.tender_review.TenderCaseCreateReq;
import com.liang.drugagent.thirdparty.db.entity.ChatSession;
import com.liang.drugagent.controller.domain.request.agent.DrugAgentReq;
import com.liang.drugagent.controller.domain.response.agent.DrugAgentResp;
import com.liang.drugagent.scene.qa.service.ChatMessageService;
import com.liang.drugagent.scene.qa.service.ChatSessionService;
import com.liang.drugagent.scene.tender_review.service.TenderCaseService;
import com.liang.drugagent.scene.tender_review.service.TenderDocumentParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 应用服务。
 *
 * <p>处理 Agent 核心业务流程编排。</p>
 *
 * @author liangjiajian
 */
@Service
public class AgentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AgentApplicationService.class);

    private final UpperAgentOrchestrator upperAgentOrchestrator;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final TenderCaseService tenderCaseService;
    private final TenderDocumentParseService tenderDocumentParseService;

    public AgentApplicationService(UpperAgentOrchestrator upperAgentOrchestrator,
                                  ChatSessionService chatSessionService,
                                  ChatMessageService chatMessageService,
                                  TenderCaseService tenderCaseService,
                                  TenderDocumentParseService tenderDocumentParseService) {
        this.upperAgentOrchestrator = upperAgentOrchestrator;
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
        this.tenderCaseService = tenderCaseService;
        this.tenderDocumentParseService = tenderDocumentParseService;
    }

    /**
     * 处理同步对话请求。
     */
    public DrugAgentResp handleChat(DrugAgentReq req) {
        log.info("Start sync agent handling via UpperAgentOrchestrator");
        DrugAgentResp resp = upperAgentOrchestrator.handle(req);
        log.info("Sync agent response ready: traceId={}, scene={}", resp.getTraceId(), resp.getScene());
        return resp;
    }

    /**
     * 处理流式对话请求。
     */
    public SseEmitter handleStreamChat(DrugAgentReq req) {
        log.info("Start stream agent handling via UpperAgentOrchestrator");
        return upperAgentOrchestrator.streamHandle(req);
    }

    /**
     * 处理带文件上传的对话请求。
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

        // 创建或获取 session
        ChatSession chatSession = getOrCreateSession(sessionId, sceneHint, userId);
        sessionId = chatSession.getId();

        // 保存用户消息
        String fileNamesJson = buildFileNamesJson(files);
        chatMessageService.addMessage(sessionId, "user", query, fileNamesJson);

        // 构建请求
        DrugAgentReq req = buildDrugAgentReq(query, sceneHint, sessionId, userId, files);

        // 场景预判，决定是否需要解析文件
        var decision = upperAgentOrchestrator.decide(req, AgentContext.from(req));
        if (decision.getScene() == SceneEnum.TENDER_REVIEW) {
            hydrateTenderMetadata(req, submittedBy, files);
        }

        // 执行主流程
        DrugAgentResp resp = handleChat(req);

        // 保存 AI 响应消息
        saveAiResponse(sessionId, resp);

        // 更新 session 标题
        updateSessionTitle(sessionId, query, chatSession);

        // 返回 sessionId 让前端同步会话
        resp.setSessionId(sessionId);
        return resp;
    }

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

    private DrugAgentReq buildDrugAgentReq(String query, String sceneHint, String sessionId, String userId, MultipartFile[] files) {
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

        List<com.liang.drugagent.scene.tender_review.model.TenderDocument> documents = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Block> blocks = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Field> fields = new ArrayList<>();
        var extractionMeta = new com.liang.drugagent.scene.tender_review.model.ExtractionMeta();
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
                if (parseResult.getExtractionMeta() != null && Boolean.FALSE.equals(parseResult.getExtractionMeta().getParseSuccess())) {
                    extractionMeta.setParseSuccess(Boolean.FALSE);
                }
            } catch (IOException e) {
                throw new IllegalStateException("文件处理失败: " + filenames.get(i), e);
            }
        }

        var data = new com.liang.drugagent.scene.tender_review.model.TenderReviewData();
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

    private com.liang.drugagent.scene.tender_review.model.TenderCase buildTenderCase(String caseId, String submittedBy, List<String> documentIds) {
        var tenderCase = new com.liang.drugagent.scene.tender_review.model.TenderCase();
        tenderCase.setCaseId(caseId);
        tenderCase.setScene("tender_review");
        tenderCase.setStatus("PARSED");
        tenderCase.setSubmittedBy(submittedBy);
        tenderCase.setCreatedAt(Instant.now());
        tenderCase.setDocumentIds(documentIds);
        return tenderCase;
    }

    private com.liang.drugagent.scene.tender_review.model.TenderDocument buildTenderDocument(String caseId, String docId, String filename) {
        var document = new com.liang.drugagent.scene.tender_review.model.TenderDocument();
        document.setCaseId(caseId);
        document.setDocumentId(docId);
        document.setFilename(filename);
        document.setDocumentName(filename);
        document.setStatus("PARSED");
        document.setFileType(resolveFileType(filename));
        return document;
    }

    private List<com.liang.drugagent.scene.tender_review.model.CompareScope> buildCompareScopes(List<String> documentIds) {
        if (documentIds == null || documentIds.size() < 2) {
            return List.of();
        }
        var compareScope = new com.liang.drugagent.scene.tender_review.model.CompareScope();
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

    private void saveAiResponse(String sessionId, DrugAgentResp resp) {
        String aiContent = resp.getAnswer() != null ? resp.getAnswer() : resp.getSummary();
        String aiMetadata = String.format(
                "{\"scene\":\"%s\",\"riskLevel\":\"%s\",\"score\":%d,\"traceId\":\"%s\"}",
                resp.getScene() != null ? resp.getScene() : "",
                resp.getRiskLevel() != null ? resp.getRiskLevel() : "",
                resp.getScore(),
                resp.getTraceId() != null ? resp.getTraceId() : ""
        );
        chatMessageService.addMessage(sessionId, "assistant", aiContent, aiMetadata);
    }

    private void updateSessionTitle(String sessionId, String query, ChatSession chatSession) {
        if (query != null && !query.isBlank() && chatSession.getTitle().equals("新对话")) {
            String title = query.length() > 20 ? query.substring(0, 20) + "..." : query;
            chatSessionService.updateSessionTitle(sessionId, title);
        }
    }
}
