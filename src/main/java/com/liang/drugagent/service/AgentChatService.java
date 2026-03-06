package com.liang.drugagent.service;

import com.liang.drugagent.advisor.PromptAugmentationAdvisor;
import com.liang.drugagent.advisor.SafetyAdvisor;
import com.liang.drugagent.prompt.SystemPromptManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

/**
 * AI 监管助手基础对话服务
 * @author liangjiajian
 */
@Service
public class AgentChatService {

    private final ChatClient chatClient;

    private final ChatMemory chatMemory;

    public AgentChatService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        // 1. 初始化 ChatClient 并附加全局 Advisor 链
        this.chatClient = chatClientBuilder
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new PromptAugmentationAdvisor(),
                        new SafetyAdvisor()
                )
                .build();
    }

    /**
     * 基础的一问一答 (兼容性方法)
     */
    public String simpleChat(String userMessage) {
        return chatWithScene(userMessage, "default", "default-user-session");
    }

    /**
     * 根据场景和会话ID执行对话 (支持多轮记忆)
     */
    public String chatWithScene(String userMessage, String agentType, String sessionId) {
        // 1. 根据场景选择 System Prompt
        String systemPromptText;
        if ("data_analysis".equals(agentType)) {
            systemPromptText = SystemPromptManager.DATA_ANALYSIS_EXPERT_PROMPT;
        } else if ("compliance_review".equals(agentType)) {
            systemPromptText = SystemPromptManager.COMPLIANCE_REVIEW_EXPERT_PROMPT;
        } else {
            systemPromptText = SystemPromptManager.DRUG_REGULATION_EXPERT_PROMPT;
        }

        // 2. 调用模型
        // Advisor 会自动根据 sessionId 从 chatMemory 提取历史消息拼接到 prompt 中
        return chatClient.prompt()
                .system(systemPromptText)
                .user(userMessage)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId)
                               .param("chat_memory_response_size", 10)) // 指定会话ID和记忆深度
                .call()
                .content();
    }

    @Deprecated
    public String chatWithScene(String userMessage, String agentType) {
        return chatWithScene(userMessage, agentType, "temp-session-" + agentType);
    }
}
