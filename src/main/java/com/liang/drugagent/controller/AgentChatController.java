package com.liang.drugagent.controller;

import com.liang.drugagent.service.AgentChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AI基础对话测试入口
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Tag(name = "AI基础联调", description = "验证大模型基建能力")
public class AgentChatController {

    private final AgentChatService agentChatService;

    @Operation(summary = "单轮同步对话", description = "用于测试 SystemPrompt 的监管干预能力")
    @PostMapping("/simple")
    public String simpleChat(@RequestBody String message) {
        return agentChatService.simpleChat(message);
    }
}
