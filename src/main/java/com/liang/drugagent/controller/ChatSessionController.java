package com.liang.drugagent.controller;

import com.liang.drugagent.agent.chat.AgentSessionService;
import com.liang.drugagent.controller.domain.request.agent.CreateSessionReq;
import com.liang.drugagent.controller.domain.request.agent.SessionMessageReq;
import com.liang.drugagent.controller.domain.request.agent.UpdateSessionTitleReq;
import com.liang.drugagent.controller.domain.response.agent.SessionMessageResp;
import com.liang.drugagent.scene.common.entity.ChatMessage;
import com.liang.drugagent.scene.common.entity.ChatSession;
import com.liang.drugagent.shared.domain.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话管理控制器。
 */
@RestController
@RequestMapping("/agent/sessions")
@RequiredArgsConstructor
@Tag(name = "Agent Sessions", description = "AI Agent 会话与消息管理")
public class ChatSessionController {

    private final AgentSessionService agentSessionService;

    @Operation(summary = "获取所有会话列表")
    @GetMapping
    public Result<List<ChatSession>> getAllSessions() {
        return Result.success(agentSessionService.getAllSessions());
    }

    @Operation(summary = "获取会话详情（含消息）")
    @GetMapping("/{id}")
    public Result<ChatSession> getSessionById(@Parameter(description = "会话ID") @PathVariable String id) {
        ChatSession session = agentSessionService.getSessionById(id);
        if (session == null) {
            return Result.error("会话不存在");
        }
        return Result.success(session);
    }

    @Operation(summary = "创建新会话")
    @PostMapping
    public Result<ChatSession> createSession(@RequestBody(required = false) CreateSessionReq request) {
        return Result.success(agentSessionService.createSession(request));
    }

    @Operation(summary = "更新会话标题")
    @PutMapping("/{id}/title")
    public Result<Void> updateSessionTitle(@Parameter(description = "会话ID") @PathVariable String id,
                                           @RequestBody UpdateSessionTitleReq request) {
        agentSessionService.updateSessionTitle(id, request);
        return Result.success(null);
    }

    @Operation(summary = "删除会话（软删除）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteSession(@Parameter(description = "会话ID") @PathVariable String id) {
        agentSessionService.deleteSession(id);
        return Result.success(null);
    }

    @Operation(summary = "清空所有会话")
    @DeleteMapping("/all")
    public Result<Void> deleteAllSessions() {
        agentSessionService.deleteAllSessions();
        return Result.success(null);
    }

    @Operation(summary = "搜索会话")
    @GetMapping("/search")
    public Result<List<ChatSession>> searchSessions(@Parameter(description = "搜索关键词") @RequestParam String q) {
        return Result.success(agentSessionService.searchSessions(q));
    }

    @Operation(summary = "获取会话的所有消息")
    @GetMapping("/{sessionId}/messages")
    public Result<List<ChatMessage>> getMessages(@Parameter(description = "会话ID") @PathVariable String sessionId) {
        return Result.success(agentSessionService.getMessages(sessionId));
    }

    @Operation(summary = "发送消息并获取AI响应")
    @PostMapping("/{sessionId}/messages")
    public Result<SessionMessageResp> addMessage(@Parameter(description = "会话ID") @PathVariable String sessionId,
                                                 @RequestBody SessionMessageReq request) {
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            return Result.error("消息内容不能为空");
        }
        return Result.success(agentSessionService.addMessage(sessionId, request));
    }
}
