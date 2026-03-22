package com.liang.drugagent.controller;

import com.liang.drugagent.domain.entity.ChatSession;
import com.liang.drugagent.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private static final String DEFAULT_USER_ID = "default_user";

    /**
     * 获取所有会话列表
     */
    @GetMapping
    public ResponseEntity<List<ChatSession>> getAllSessions() {
        List<ChatSession> sessions = chatSessionService.getSessionsByUserId(DEFAULT_USER_ID);
        return ResponseEntity.ok(sessions);
    }

    /**
     * 获取单个会话详情（含消息）
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChatSession> getSessionById(@PathVariable String id) {
        ChatSession session = chatSessionService.getSessionWithMessages(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(session);
    }

    /**
     * 创建新会话
     */
    @PostMapping
    public ResponseEntity<ChatSession> createSession(@RequestBody Map<String, String> request) {
        String title = request.getOrDefault("title", "新会话");
        String scene = request.getOrDefault("scene", "general");
        ChatSession session = chatSessionService.createSession(title, scene, DEFAULT_USER_ID);
        return ResponseEntity.ok(session);
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/{id}/title")
    public ResponseEntity<Void> updateSessionTitle(@PathVariable String id, @RequestBody Map<String, String> request) {
        String title = request.get("title");
        chatSessionService.updateSessionTitle(id, title);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除会话（软删除）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable String id) {
        chatSessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 搜索会话
     */
    @GetMapping("/search")
    public ResponseEntity<List<ChatSession>> searchSessions(@RequestParam String q) {
        List<ChatSession> sessions = chatSessionService.searchSessions(DEFAULT_USER_ID, q);
        return ResponseEntity.ok(sessions);
    }
}
