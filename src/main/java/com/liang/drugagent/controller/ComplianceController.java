package com.liang.drugagent.controller;

import com.liang.drugagent.domain.ComplianceChatRequest;
import com.liang.drugagent.domain.GeneralResponse;
import com.liang.drugagent.service.AgentChatService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 合规审查对话接口
 */
@RestController
@RequestMapping("/compliance")
public class ComplianceController {

    private final AgentChatService agentChatService;

    public ComplianceController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    // ----- 核心的大模型调用通道 -----

    @Operation(summary = "发送对话到合规审查Agent")
    @PostMapping("/chat/send")
    public GeneralResponse<Map<String, String>> sendChat(@RequestBody ComplianceChatRequest request) {
        // 透传来自前端的 sessionId (如果不存在则使用默认值)
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default-compliance-session";
        String aiResponse = agentChatService.chatWithScene(request.getMessage(), "compliance_review", sessionId);
        return GeneralResponse.success(Map.of("content", aiResponse));
    }


    // ----- 以下为保障前端渲染不报错的 Mock 接口 -----

    @Operation(summary = "Mock获取会话列表")
    @GetMapping("/sessions")
    public GeneralResponse<List<Map<String, Object>>> getSessions(@RequestParam(required = false) String type) {
        return GeneralResponse.success(List.of(
            Map.of("id", "mock-session-1", "title", "新法规知识问答"),
            Map.of("id", "mock-session-2", "title", "采购单据核验记录")
        ));
    }

    @Operation(summary = "Mock创建新会话")
    @PostMapping("/sessions")
    public GeneralResponse<Map<String, Object>> createSession(@RequestBody Map<String, Object> data) {
        String newId = UUID.randomUUID().toString();
        return GeneralResponse.success(Map.of("id", newId, "title", "新合规会话"));
    }

    @Operation(summary = "Mock删除会话")
    @DeleteMapping("/sessions/{id}")
    public GeneralResponse<String> deleteSession(@PathVariable String id) {
        return GeneralResponse.success("ok");
    }

    @Operation(summary = "Mock获取会话记录")
    @GetMapping("/sessions/{sessionId}/messages")
    public GeneralResponse<List<Map<String, String>>> getMessages(@PathVariable String sessionId) {
        return GeneralResponse.success(new ArrayList<>(List.of(
            Map.of("role", "assistant", "content", "您好，我是受国家药监局规范约束的 AI 合规助手。您可以通过我查询或审查相关单据是否符合现行法规。")
        )));
    }

    @Operation(summary = "Mock上传文件解析")
    @PostMapping("/file/upload")
    public GeneralResponse<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        return GeneralResponse.success(Map.of(
            "id", UUID.randomUUID().toString(),
            "fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "未知文件.pdf"
        ));
    }
}
