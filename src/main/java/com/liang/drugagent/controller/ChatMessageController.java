package com.liang.drugagent.controller;

import com.liang.drugagent.agent.UpperAgentOrchestrator;
import com.liang.drugagent.domain.entity.ChatMessage;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.resp.DrugAgentResp;
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
    private final UpperAgentOrchestrator upperAgentOrchestrator;

    /**
     * 获取会话的所有消息
     */
    @GetMapping
    public ResponseEntity<List<ChatMessage>> getMessages(@PathVariable String sessionId) {
        List<ChatMessage> messages = chatMessageService.getMessagesBySessionId(sessionId);
        return ResponseEntity.ok(messages);
    }

    /**
     * 添加用户消息并触发 AI 响应
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> addMessage(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        String role = request.get("role");
        String content = request.get("content");
        String metadata = request.get("metadata");

        // 保存用户消息
        chatMessageService.addMessage(sessionId, role, content, metadata);

        // 如果是用户消息，触发 AI 响应（通过 UpperAgentOrchestrator 做意图理解和路由）
        DrugAgentResp aiResponse = null;
        if ("user".equals(role)) {
            DrugAgentReq req = DrugAgentReq.builder()
                    .sessionId(sessionId)
                    .query(content)
                    .build();
            aiResponse = upperAgentOrchestrator.handle(req);
            // 保存 AI 响应消息
            chatMessageService.addMessage(sessionId, "assistant", aiResponse.getAnswer(), null);
        }

        return ResponseEntity.ok(Map.of(
                "userMessage", content,
                "aiResponse", aiResponse != null ? aiResponse.getAnswer() : "",
                "traceId", aiResponse != null ? aiResponse.getTraceId() : null,
                "scene", aiResponse != null ? aiResponse.getScene() : null,
                "routeSource", aiResponse != null ? aiResponse.getRouteSource() : null,
                "routeReason", aiResponse != null ? aiResponse.getRouteReason() : null,
                "confidence", aiResponse != null ? aiResponse.getConfidence() : null
        ));
    }
}
