package com.liang.drugagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.chat.AgentChatService;
import com.liang.drugagent.agent.chat.AgentMessageService;
import com.liang.drugagent.agent.chat.AgentSessionService;
import com.liang.drugagent.agent.chat.LLMChatService;
import com.liang.drugagent.agent.common.entity.ChatMessage;
import com.liang.drugagent.agent.common.entity.ChatSession;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.*;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.facade.TenderReviewSceneService;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.shared.llm.LlmProviderType;
import com.liang.drugagent.shared.llm.ModelInfo;
import com.liang.drugagent.shared.model.ThinkingStepProgress;
import com.liang.drugagent.shared.model.Result;
import com.liang.drugagent.shared.model.WorkflowResult;
import com.liang.drugagent.shared.rag.cos.TencentCosStorageService;
import com.liang.drugagent.shared.rag.entity.OssFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 统一控制器。
 *
 * <p>聚焦 AI 对话入口能力：
 * <ul>
 *   <li>AI 对话：同步/流式对话、文件上传</li>
 *   <li>会话管理：CRUD、搜索、消息管理</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Tag(name = "Agent", description = "AI Agent 对话与会话管理")
public class AgentController {

    private final AgentChatService agentChatService;
    private final AgentSessionService agentSessionService;
    private final AgentMessageService agentMessageService;
    private final LLMChatService llmChatService;
    private final TenderReviewSceneService tenderReviewSceneService;
    private final TencentCosStorageService cosStorageService;
    private final ObjectMapper objectMapper;

    // ==================== 对话接口 ====================

    @Operation(summary = "获取可用模型列表")
    @GetMapping("/models")
    public Result<List<ModelInfo>> getAvailableModels() {
        return Result.success(llmChatService.getAvailableModels());
    }

    @Operation(summary = "同步对话")
    @PostMapping("/chat")
    public Object chat(@RequestBody AgentChatReq req) {
        // 流式请求降级为同步响应，避免流式处理复杂场景时崩溃
        if (Boolean.TRUE.equals(req.getStream())) {
            log.info("[AgentController] stream=true 请求降级为同步处理");
            return Result.success(agentChatService.chat(req));
        }
        return Result.success(agentChatService.chat(req));
    }

    @Operation(summary = "流式对话")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentChatResp>> streamChat(@RequestBody AgentChatReq req) {
        return agentChatService.streamChat(req);
    }

    @Operation(summary = "文件上传对话（multipart/form-data）")
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AgentChatResp> submit(
            @RequestParam(value = "req", required = false) String reqJson,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {
        try {
            log.info("[AgentController] 收到文件上传请求, reqJson长度={}, reqJson内容={}, fileCount={}",
                    reqJson != null ? reqJson.length() : 0, reqJson, files != null ? files.length : 0);
            if (reqJson == null || reqJson.isBlank()) {
                log.error("[AgentController] req 参数为空");
                return Result.error("req 参数不能为空");
            }
            AgentChatReq req = objectMapper.readValue(reqJson, AgentChatReq.class);
            if (req.getQuery() == null || req.getQuery().isBlank()) {
                log.error("[AgentController] query 参数为空");
                return Result.error("query 不能为空");
            }
            if (files != null && files.length > 0) {
                req.setFiles(files);
            }
            return Result.success(agentChatService.chat(req));
        } catch (Exception e) {
            log.error("[AgentController] 文件上传请求处理失败: {}", e.getMessage(), e);
            return Result.error("请求处理失败: " + e.getMessage());
        }
    }

    /**
     * 文件上传对话（JSON格式，降级使用）。
     */
    @Operation(summary = "文件上传对话（JSON格式）")
    @PostMapping(value = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<AgentChatResp> submitJson(@RequestBody AgentChatReq req) {
        log.info("[AgentController] 收到JSON格式提交请求");
        return Result.success(agentChatService.chat(req));
    }

    /**
     * 流式文件上传对话（SSE 实时推送思考步骤进度）。
     */
    @Operation(summary = "流式文件上传对话（SSE）")
    @PostMapping(value = "/submit/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ThinkingStepProgress>> submitStream(
            @RequestParam(value = "req", required = false) String reqJson,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {
        try {
            log.info("[AgentController] 收到流式文件上传请求, reqJson长度={}, fileCount={}",
                    reqJson != null ? reqJson.length() : 0, files != null ? files.length : 0);
            if (reqJson == null || reqJson.isBlank()) {
                log.error("[AgentController] req 参数为空");
                return Flux.error(new IllegalArgumentException("req 参数不能为空"));
            }
            AgentChatReq req = objectMapper.readValue(reqJson, AgentChatReq.class);
            if (req.getQuery() == null || req.getQuery().isBlank()) {
                log.error("[AgentController] query 参数为空");
                return Flux.error(new IllegalArgumentException("query 不能为空"));
            }
            if (files != null && files.length > 0) {
                req.setFiles(files);
            }

            ChatSession session = agentSessionService.getOrCreateSession(req.getSessionId());
            String sessionId = session.getId();
            List<OssFile> uploadedFiles = saveUploadedFiles(sessionId, req);
            mergeFileIds(req, uploadedFiles);

            List<ChatMessage> recentMessages = agentMessageService.getRecentMessages(sessionId, 20);
            AgentChatContext context = AgentChatContext.from(req, sessionId);
            context.setSession(session);
            context.setHistoryMessages(recentMessages);
            context.setRecentSummary(session.getSummary());
            context.setUploadedFiles(uploadedFiles);

            agentMessageService.saveUserMessage(sessionId, req.getQuery(), null);
            agentSessionService.increaseMessageCount(sessionId, 1);

            return tenderReviewSceneService.streamExecute(context, req)
                    .doOnNext(progress -> {
                        if (progress != null && progress.isFinalResult()) {
                            persistStreamFinalResult(sessionId, context, progress);
                        }
                    })
                    .map(progress -> ServerSentEvent.<ThinkingStepProgress>builder()
                            .data(progress)
                            .build());
        } catch (Exception e) {
            log.error("[AgentController] 流式文件上传请求处理失败: {}", e.getMessage(), e);
            return Flux.error(e);
        }
    }

    private List<OssFile> saveUploadedFiles(String sessionId, AgentChatReq req) {
        if (req.getFiles() == null || req.getFiles().length == 0) {
            return List.of();
        }
        return cosStorageService.saveUploadedFiles(sessionId, req.getFiles());
    }

    private void mergeFileIds(AgentChatReq req, List<OssFile> uploadedFiles) {
        if (uploadedFiles == null || uploadedFiles.isEmpty()) {
            return;
        }
        List<String> mergedFileIds = new ArrayList<>();
        if (req.getFileIds() != null && !req.getFileIds().isEmpty()) {
            mergedFileIds.addAll(req.getFileIds());
        }
        uploadedFiles.stream()
                .map(OssFile::getId)
                .filter(id -> id != null && !id.isBlank())
                .forEach(id -> {
                    if (!mergedFileIds.contains(id)) {
                        mergedFileIds.add(id);
                    }
                });
        req.setFileIds(mergedFileIds);
    }

    private void persistStreamFinalResult(String sessionId,
                                          AgentChatContext context,
                                          ThinkingStepProgress progress) {
        WorkflowResult workflowResult = progress.getResult();
        if (workflowResult == null) {
            return;
        }

        String assistantContent = workflowResult.getAnswer() != null && !workflowResult.getAnswer().isBlank()
                ? workflowResult.getAnswer()
                : workflowResult.getSummary();

        String messageType = (workflowResult.getReport() != null || workflowResult.getRiskLevel() != null)
                ? "assistant_result_card"
                : "assistant_text";

        try {
            Map<String, Object> metadata = buildStreamMetadata(context, progress, workflowResult);
            String metadataJson = objectMapper.writeValueAsString(metadata);
            agentMessageService.saveAssistantMessage(sessionId, assistantContent, metadataJson, messageType);
        } catch (Exception ex) {
            log.warn("[AgentController] 流式结果 metadata 序列化失败，将降级为纯文本消息: {}", ex.getMessage());
            agentMessageService.saveAssistantMessage(sessionId, assistantContent, null, messageType);
        }

        agentSessionService.touchSession(sessionId, SceneEnum.TENDER_REVIEW.name());
        agentSessionService.increaseMessageCount(sessionId, 1);

        String sessionTitle = progress.getSessionTitle() != null && !progress.getSessionTitle().isBlank()
                ? progress.getSessionTitle()
                : workflowResult.getSessionTitle();
        if (sessionTitle != null && !sessionTitle.isBlank()) {
            agentSessionService.updateSessionTitleIfNeeded(sessionId, sessionTitle);
        }
        if (workflowResult.getSummary() != null && !workflowResult.getSummary().isBlank()) {
            agentSessionService.updateSessionSummary(sessionId, workflowResult.getSummary());
        }
    }

    private Map<String, Object> buildStreamMetadata(AgentChatContext context,
                                                    ThinkingStepProgress progress,
                                                    WorkflowResult workflowResult) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("traceId", context.getTraceId());
        metadata.put("scene", SceneEnum.TENDER_REVIEW.name());
        metadata.put("summary", workflowResult.getSummary());
        metadata.put("answer", workflowResult.getAnswer());
        metadata.put("riskLevel", workflowResult.getRiskLevel());
        metadata.put("score", workflowResult.getScore());
        metadata.put("report", workflowResult.getReport());
        metadata.put("evidenceList", workflowResult.getEvidenceList());
        metadata.put("evidenceGroups", workflowResult.getEvidenceGroups());
        metadata.put("steps", workflowResult.getSteps());
        metadata.put("thinkingSteps", workflowResult.getThinkingSteps());
        metadata.put("sessionTitle", progress.getSessionTitle() != null ? progress.getSessionTitle() : workflowResult.getSessionTitle());
        metadata.put("documentIds", progress.getDocumentIds() != null ? progress.getDocumentIds() : workflowResult.getDocumentIds());
        metadata.put("documentNames", resolveDocumentNames(context, workflowResult));
        metadata.put("fileIds", progress.getDocumentIds() != null ? progress.getDocumentIds() : workflowResult.getDocumentIds());
        return metadata;
    }

    private List<String> resolveDocumentNames(AgentChatContext context, WorkflowResult workflowResult) {
        if (workflowResult.getDocumentNames() != null && !workflowResult.getDocumentNames().isEmpty()) {
            return workflowResult.getDocumentNames();
        }
        Object tenderReviewDataObj = context.getMetadata().get("tenderReviewData");
        if (tenderReviewDataObj instanceof TenderReviewData tenderReviewData
                && tenderReviewData.getDocuments() != null
                && !tenderReviewData.getDocuments().isEmpty()) {
            return tenderReviewData.getDocuments().stream()
                    .map(doc -> doc.getDocumentName() != null ? doc.getDocumentName()
                            : (doc.getFilename() != null ? doc.getFilename() : doc.getDocumentId()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    // ==================== 会话管理接口 ====================

    @Operation(summary = "获取所有会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSession>> getSessions() {
        List<ChatSession> sessions = agentSessionService.getAllSessions();
        return Result.success(sessions);
    }

    @Operation(summary = "获取会话详情（含消息）")
    @GetMapping("/sessions/{id}")
    public Result<ChatSession> getSessionById(@PathVariable("id") String id) {
        ChatSession session = agentSessionService.getSessionWithMessages(id);
        if (session == null) {
            return Result.error("会话不存在");
        }
        return Result.success(session);
    }

    @Operation(summary = "创建新会话")
    @PostMapping("/sessions")
    public Result<ChatSession> createSession(@RequestBody CreateSessionReq req) {
        String title = req.getTitle() != null ? req.getTitle() : "新对话";
        String userId = req.getUserId() != null ? req.getUserId() : "default";
        ChatSession session = agentSessionService.createSession(title, userId);
        return Result.success(session);
    }

    @Operation(summary = "更新会话标题")
    @PutMapping("/sessions/{id}/title")
    public Result<Void> updateSessionTitle(@PathVariable("id") String id, @RequestBody UpdateSessionTitleReq req) {
        agentSessionService.updateSessionTitle(id, req.getTitle());
        return Result.success(null);
    }

    @Operation(summary = "删除会话（软删除）")
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable("id") String id) {
        agentSessionService.deleteSession(id);
        return Result.success(null);
    }

    @Operation(summary = "搜索会话")
    @GetMapping("/sessions/search")
    public Result<List<ChatSession>> searchSessions(@RequestParam("q") String keyword) {
        List<ChatSession> sessions = agentSessionService.searchSessions(keyword);
        return Result.success(sessions);
    }

    // ==================== 消息管理接口 ====================

    @Operation(summary = "获取会话的所有消息")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> getMessages(@PathVariable("sessionId") String sessionId) {
        List<ChatMessage> messages = agentMessageService.getMessagesBySessionId(sessionId);
        return Result.success(messages);
    }

    @Operation(summary = "发送消息并获取AI响应")
    @PostMapping("/sessions/{sessionId}/messages")
    public Result<AgentChatResp> addMessage(
            @PathVariable("sessionId") String sessionId,
            @RequestBody SessionMessageReq req) {
        // 先保存用户消息
        agentMessageService.saveUserMessage(sessionId, req.getContent(), null);

        // 构建对话请求
        AgentChatReq chatReq = AgentChatReq.builder()
                .sessionId(sessionId)
                .query(req.getContent())
                .model(req.getModel())
                .build();

        // 调用AI处理
        AgentChatResp resp = agentChatService.chat(chatReq);

        return Result.success(resp);
    }

}
