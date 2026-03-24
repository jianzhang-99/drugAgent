package com.liang.drugagent.controller.agent;

import com.liang.drugagent.agent.chat.AgentChatService;
import com.liang.drugagent.controller.domain.request.agent.DrugAgentReq;
import com.liang.drugagent.controller.domain.response.agent.DrugAgentResp;
import com.liang.drugagent.scene.common.service.ChatMemoryService;
import com.liang.drugagent.shared.domain.response.Result;
import com.liang.drugagent.scene.common.entity.ChatMessage;
import com.liang.drugagent.scene.common.entity.ChatSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Agent 统一控制器。
 *
 * <p>整合 AI 对话与会话管理功能：
 * <ul>
 *   <li>AI 对话：同步/流式对话、文件上传</li>
 *   <li>会话管理：会话 CRUD、消息存取</li>
 * </ul>
 *
 * @author liangjiajian
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "Agent", description = "AI Agent 对话与会话管理")
@CrossOrigin(origins = "*")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final String DEFAULT_USER_ID = "default_user";

    private final AgentChatService agentChatService;
    private final ChatMemoryService chatMemoryService;

    // ==================== AI 对话接口 ====================

    @Operation(summary = "同步对话")
    @PostMapping("/chat")
    public Result<DrugAgentResp> chat(@RequestBody DrugAgentReq req) {
        if (req == null || ((req.getQuery() == null || req.getQuery().isBlank())
                && (req.getFileIds() == null || req.getFileIds().isEmpty()))) {
            log.warn("Reject empty chat request");
            return Result.error("query 和 fileIds 不能同时为空");
        }
        log.info("Receive sync chat request: sessionId={}, userId={}, queryLength={}",
                req.getSessionId(), req.getUserId(), req.getQuery() == null ? 0 : req.getQuery().length());
        return Result.success(agentChatService.handleChat(req));
    }

    @Operation(summary = "文件上传对话")
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DrugAgentResp> submit(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "sceneHint", required = false) String sceneHint,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "submittedBy", defaultValue = "anonymous") String submittedBy,
            @RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return Result.error("请至少上传一个文件");
        }
        return Result.success(agentChatService.handleFileUpload(query, sceneHint, sessionId, userId, submittedBy, files));
    }

    @Operation(summary = "流式对话")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody DrugAgentReq req) {
        log.info("Receive stream chat request: sessionId={}, userId={}, queryLength={}",
                req == null ? null : req.getSessionId(),
                req == null ? null : req.getUserId(),
                req == null || req.getQuery() == null ? 0 : req.getQuery().length());
        return agentChatService.handleStreamChat(req);
    }

    // ==================== 会话管理接口 ====================

    @Operation(summary = "获取所有会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSession>> getAllSessions() {
        List<ChatSession> sessions = chatMemoryService.getSessionsByUserId(DEFAULT_USER_ID);
        return Result.success(sessions);
    }

    @Operation(summary = "获取会话详情（含消息）")
    @GetMapping("/sessions/{id}")
    public Result<ChatSession> getSessionById(
            @Parameter(description = "会话ID") @PathVariable String id) {
        ChatSession session = chatMemoryService.getSessionWithMessages(id);
        if (session == null) {
            return Result.error("会话不存在");
        }
        return Result.success(session);
    }

    @Operation(summary = "创建新会话")
    @PostMapping("/sessions")
    public Result<ChatSession> createSession(@RequestBody Map<String, String> request) {
        String title = request.getOrDefault("title", "新对话");
        String scene = request.getOrDefault("scene", "general");
        ChatSession session = chatMemoryService.createSession(title, scene, DEFAULT_USER_ID);
        return Result.success(session);
    }

    @Operation(summary = "更新会话标题")
    @PutMapping("/sessions/{id}/title")
    public Result<Void> updateSessionTitle(
            @Parameter(description = "会话ID") @PathVariable String id,
            @RequestBody Map<String, String> request) {
        String title = request.get("title");
        chatMemoryService.updateSessionTitle(id, title);
        return Result.success(null);
    }

    @Operation(summary = "删除会话（软删除）")
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(
            @Parameter(description = "会话ID") @PathVariable String id) {
        chatMemoryService.deleteSession(id);
        return Result.success(null);
    }

    @Operation(summary = "搜索会话")
    @GetMapping("/sessions/search")
    public Result<List<ChatSession>> searchSessions(
            @Parameter(description = "搜索关键词") @RequestParam String q) {
        List<ChatSession> sessions = chatMemoryService.searchSessions(DEFAULT_USER_ID, q);
        return Result.success(sessions);
    }

    @Operation(summary = "获取会话的所有消息")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> getMessages(
            @Parameter(description = "会话ID") @PathVariable String sessionId) {
        List<ChatMessage> messages = chatMemoryService.getMessagesBySessionId(sessionId);
        return Result.success(messages);
    }

    @Operation(summary = "发送消息并获取AI响应")
    @PostMapping("/sessions/{sessionId}/messages")
    public Result<Map<String, Object>> addMessage(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        String role = request.get("role");
        String content = request.get("content");
        String metadata = request.get("metadata");

        chatMemoryService.addMessage(sessionId, role, content, metadata);

        DrugAgentResp aiResponse = null;
        if ("user".equals(role)) {
            DrugAgentReq req = DrugAgentReq.builder()
                    .sessionId(sessionId)
                    .query(content)
                    .build();
            aiResponse = agentChatService.handleChat(req);
            chatMemoryService.addMessage(sessionId, "assistant", aiResponse.getAnswer(), null);
        }

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("userMessage", content);
        response.put("aiResponse", aiResponse != null ? aiResponse.getAnswer() : "");
        response.put("traceId", aiResponse != null ? aiResponse.getTraceId() : null);
        response.put("scene", aiResponse != null ? aiResponse.getScene() : null);
        response.put("routeSource", aiResponse != null ? aiResponse.getRouteSource() : null);
        response.put("routeReason", aiResponse != null ? aiResponse.getRouteReason() : null);
        response.put("confidence", aiResponse != null ? aiResponse.getConfidence() : null);

        return Result.success(response);
    }
}
