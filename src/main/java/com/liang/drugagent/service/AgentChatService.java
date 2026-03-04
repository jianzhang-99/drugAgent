package com.liang.drugagent.service;

import com.liang.drugagent.prompt.SystemPromptManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 监管助手基础对话服务
 */
@Service
public class AgentChatService {

    private final ChatClient chatClient;

    public AgentChatService(ChatClient.Builder chatClientBuilder) {
        // 使用 Spring AI 提供的 Builder 注入千问模型客户端
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 基础的一问一答 (非流式返回)
     * @param userMessage 用户问题
     * @return 监管助手的回答
     */
    public String simpleChat(String userMessage) {
        
        // 构建 SystemMessage，定义 AI 角色（使用之前编写的“药品监管专家”Prompt）
        var systemMessage = new SystemPromptTemplate(SystemPromptManager.DRUG_REGULATION_EXPERT_PROMPT).createMessage();
        
        // 构建用户的具体问题 Message
        var uMessage = new UserMessage(userMessage);

        // 拼接 Prompt 提交给千问大模型
        Prompt prompt = new Prompt(List.of(systemMessage, uMessage));

        // 同步调用获取响应文本
        return chatClient.prompt(prompt).call().content();
    }
}
