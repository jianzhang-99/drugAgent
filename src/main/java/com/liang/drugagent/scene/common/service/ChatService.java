package com.liang.drugagent.scene.common.service;

import com.liang.drugagent.shared.advisor.LoggingAdvisor;
import com.liang.drugagent.shared.advisor.PromptAdvisor;
import com.liang.drugagent.shared.advisor.SafetyAdvisor;
import com.liang.drugagent.agent.prompt.SystemPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 基础模型对话服务。
 *
 * <p>作为AI对话的统一入口，封装Spring AI ChatClient并附加全局Advisor链：</p>
 * <ul>
 *   <li>{@link MessageChatMemoryAdvisor} - 会话记忆，支持多轮对话上下文</li>
 *   <li>{@link PromptAdvisor} - Prompt增强，优化输入提示词</li>
 *   <li>{@link SafetyAdvisor} - 安全审查，过滤敏感内容</li>
 *   <li>{@link LoggingAdvisor} - 请求日志，记录对话轨迹</li>
 * </ul>
 *
 * <p>支持两种对话模式：</p>
 * <ul>
 *   <li>同步对话 ({@code simpleChat}) - 适用于简单问答场景</li>
 *   <li>流式对话 ({@code streamChatWithScene}) - 适用于SSE打字机效果</li>
 * </ul>
 *
 * <p>会根据{@code agentType}自动选择对应的System Prompt，当前支持：</p>
 * <ul>
 *   <li>default - 默认医药监管专家角色</li>
 *   <li>data_analysis - 数据分析专家</li>
 *   <li>compliance_review - 合规审查专家</li>
 * </ul>
 *
 * @author liangjiajian
 * @see ChatClient
 * @see ChatMemory
 */
@Service
public class ChatService {

    /**
     * Spring AI ChatClient 实例，用于与AI模型交互。
     */
    private final ChatClient chatClient;

    /**
     * 构造方法，注入ChatClient构建器并配置全局Advisor链。
     *
     * @param chatClientBuilder ChatClient构建器
     * @param chatMemory 会话记忆存储，用于多轮对话上下文管理
     */
    public ChatService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        // 1. 初始化 ChatClient 并附加全局 Advisor 链
        this.chatClient = chatClientBuilder
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new PromptAdvisor(),
                        new SafetyAdvisor(),
                        new LoggingAdvisor()
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
        String systemPromptText = resolveSystemPrompt(agentType);

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

    /**
     * 根据场景和会话ID执行流式对话，适合前端 SSE 打字机效果。
     */
    public Flux<String> streamChatWithScene(String userMessage, String agentType, String sessionId) {
        String systemPromptText = resolveSystemPrompt(agentType);

        return chatClient.prompt()
                .system(systemPromptText)
                .user(userMessage)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId)
                        .param("chat_memory_response_size", 10))
                .stream()
                .content();
    }

    private String resolveSystemPrompt(String agentType) {
        // 1. 根据场景选择 System Prompt
        if ("data_analysis".equals(agentType)) {
            return SystemPrompt.DATA_ANALYSIS_EXPERT_PROMPT;
        } else if ("compliance_review".equals(agentType)) {
            return SystemPrompt.COMPLIANCE_REVIEW_EXPERT_PROMPT;
        }
        return SystemPrompt.DRUG_REGULATION_EXPERT_PROMPT;
    }
}
