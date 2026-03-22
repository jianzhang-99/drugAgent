package com.liang.drugagent.controller;

import com.liang.drugagent.domain.entity.ChatMessage;
import com.liang.drugagent.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions/{sessionId}/messages")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    /**
     * 获取会话的所有消息
     */
    @GetMapping
    public ResponseEntity<List<ChatMessage>> getMessages(@PathVariable String sessionId) {
        List<ChatMessage> messages = chatMessageService.getMessagesBySessionId(sessionId);
        return ResponseEntity.ok(messages);
    }

    /**
     * 添加消息
     */
    @PostMapping
    public ResponseEntity<ChatMessage> addMessage(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        String role = request.get("role");
        String content = request.get("content");
        String metadata = request.get("metadata");
        ChatMessage message = chatMessageService.addMessage(sessionId, role, content, metadata);
        return ResponseEntity.ok(message);
    }
}
