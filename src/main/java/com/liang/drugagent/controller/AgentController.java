package com.liang.drugagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.chat.AgentChatService;
import com.liang.drugagent.agent.chat.AgentMessageService;
import com.liang.drugagent.agent.chat.AgentSessionService;
import com.liang.drugagent.agent.common.entity.ChatMessage;
import com.liang.drugagent.agent.common.entity.ChatSession;
import com.liang.drugagent.controller.domain.request.agent.*;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.shared.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    private final ObjectMapper objectMapper;

    // ==================== 对话接口 ====================

    @Operation(summary = "同步对话")
    @PostMapping("/chat")
    public Result<AgentChatResp> chat(@RequestBody AgentChatReq req) {
        return Result.success(agentChatService.chat(req));
    }

    @Operation(summary = "文件上传对话（multipart/form-data）")
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AgentChatResp> submit(
            @RequestParam("req") String reqJson,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {
        try {
            log.info("[AgentController] 收到文件上传请求, reqJson长度={}, fileCount={}",
                    reqJson != null ? reqJson.length() : 0, files != null ? files.length : 0);
            AgentChatReq req = objectMapper.readValue(reqJson, AgentChatReq.class);
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
                .build();

        // 调用AI处理
        AgentChatResp resp = agentChatService.chat(chatReq);

        return Result.success(resp);
    }

}
