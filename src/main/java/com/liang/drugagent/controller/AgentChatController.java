package com.liang.drugagent.controller;

import com.liang.drugagent.advisor.LoggingAdvisor;
import com.liang.drugagent.prompt.DrugAnalysisPrompt;
import com.liang.drugagent.service.AgentChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.*;

/**
 * AI基础对话测试入口
 */
@RestController
@RequestMapping("/chat")
public class AgentChatController {

    private final AgentChatService agentChatService;

    public AgentChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @Operation(summary = "单轮同步对话", description = "用于测试 SystemPrompt 的监管干预能力")
    @PostMapping("/simple")
    public String simpleChat(@RequestBody String message) {
        return agentChatService.simpleChat(message);
    }
}
