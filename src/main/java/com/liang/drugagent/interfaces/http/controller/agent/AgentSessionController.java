package com.liang.drugagent.interfaces.http.controller.agent;

import com.liang.drugagent.core.agent.UpperAgentOrchestrator;
import com.liang.drugagent.integrations.storage.entity.ChatMessage;
import com.liang.drugagent.integrations.storage.entity.ChatSession;
import com.liang.drugagent.interfaces.http.request.agent.DrugAgentReq;
import com.liang.drugagent.interfaces.http.response.agent.DrugAgentResp;
import com.liang.drugagent.scenes.general_qa.application.services.ChatMessageService;
import com.liang.drugagent.scenes.general_qa.application.services.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent 会话管理控制器。
 *
 * <p>统一处理会话和消息操作。</p>
 *
 * @author liangjiajian
 */
@RestController
@RequestMapping("/api/agent/sessions")
@RequiredArgsConstructor
@Tag(name = "Agent会话", description = "会话生命周期管理")
@CrossOrigin(origins = "*")
public class AgentSessionController {

    private static final String DEFAULT_USER_ID = "default_user";

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final UpperAgentOrchestrator upperAgentOrchestrator;

    @Operation(summary = "获取所有会话列表")
    @GetMapping
    public ResponseEntity<List<ChatSession>> getAllSessions() {
        List<ChatSession> sessions = chatSessionService.getSessionsByUserId(DEFAULT_USER_ID);
        return ResponseEntity.ok(sessions);
    }

    @Operation(summary = "获取单个会话详情（含消息）")
    @GetMapping("/{id}")
    public ResponseEntity<ChatSession> getSessionById(
            @Parameter(description = "会话ID") @PathVariable String id) {
        ChatSession session = chatSessionService.getSessionWithMessages(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(session);
    }

    @Operation(summary = "创建新会话")
    @PostMapping
    public ResponseEntity<ChatSession> createSession(@RequestBody Map<String, String> request) {
        String title = request.getOrDefault("title", "新会话");
        String scene = request.getOrDefault("scene", "general");
        ChatSession session = chatSessionService.createSession(title, scene, DEFAULT_USER_ID);
        return ResponseEntity.ok(session);
    }

    @Operation(summary = "更新会话标题")
    @PutMapping("/{id}/title")
    public ResponseEntity<Void> updateSessionTitle(
            @Parameter(description = "会话ID") @PathVariable String id,
            @RequestBody Map<String, String> request) {
        String title = request.get("title");
        chatSessionService.updateSessionTitle(id, title);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "删除会话（软删除）")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(
            @Parameter(description = "会话ID") @PathVariable String id) {
        chatSessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "搜索会话")
    @GetMapping("/search")
    public ResponseEntity<List<ChatSession>> searchSessions(
            @Parameter(description = "搜索关键词") @RequestParam String q) {
        List<ChatSession> sessions = chatSessionService.searchSessions(DEFAULT_USER_ID, q);
        return ResponseEntity.ok(sessions);
    }

    @Operation(summary = "获取会话的所有消息")
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ChatMessage>> getMessages(
            @Parameter(description = "会话ID") @PathVariable String sessionId) {
        List<ChatMessage> messages = chatMessageService.getMessagesBySessionId(sessionId);
        return ResponseEntity.ok(messages);
    }

    @Operation(summary = "添加消息并触发AI响应")
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> addMessage(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        String role = request.get("role");
        String content = request.get("content");
        String metadata = request.get("metadata");

        chatMessageService.addMessage(sessionId, role, content, metadata);

        DrugAgentResp aiResponse = null;
        if ("user".equals(role)) {
            DrugAgentReq req = DrugAgentReq.builder()
                    .sessionId(sessionId)
                    .query(content)
                    .build();
            aiResponse = upperAgentOrchestrator.handle(req);
            chatMessageService.addMessage(sessionId, "assistant", aiResponse.getAnswer(), null);
        }

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("userMessage", content);
        response.put("aiResponse", aiResponse != null ? aiResponse.getAnswer() : "");
        response.put("traceId", aiResponse != null ? aiResponse.getTraceId() : null);
        response.put("scene", aiResponse != null ? aiResponse.getScene() : null);
        response.put("routeSource", aiResponse != null ? aiResponse.getRouteSource() : null);
        response.put("routeReason", aiResponse != null ? aiResponse.getRouteReason() : null);
        response.put("confidence", aiResponse != null ? aiResponse.getConfidence() : null);

        return ResponseEntity.ok(response);
    }
}
