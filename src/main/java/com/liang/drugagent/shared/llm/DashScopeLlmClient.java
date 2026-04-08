package com.liang.drugagent.shared.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 阿里云百炼(DashScope) LLM 客户端 - OpenAI 兼容模式。
 *
 * <p>直接使用 Spring AI 自动装配的 OpenAI ChatModel 接入百炼兼容端点，
 * 避免维护手写的 ChatModel 适配层。
 */
@Slf4j
@Component
public class DashScopeLlmClient implements LlmClient {

    private final ChatClient chatClient;

    public DashScopeLlmClient(@Qualifier("openAiChatModel") ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Override
    public LlmProviderType getProvider() {
        return LlmProviderType.DASHSCOPE;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        log.debug("DashScope(OpenAI兼容)聊天请求 - sessionId: {}, 模型: {}",
                request.getSessionId(), request.getModel());

        try {
            String response = chatClient.prompt()
                    .messages(buildMessages(request))
                    .options(buildOptions(request))
                    .advisors(a -> a.param("chat_memory_conversation_id", request.getSessionId())
                            .param("chat_memory_response_size", 10))
                    .call()
                    .content();

            log.debug("DashScope(OpenAI兼容)聊天响应 - sessionId: {}, 响应长度: {}",
                    request.getSessionId(), response.length());

            return LlmResponse.success(response, LlmProviderType.DASHSCOPE, request.getModel());
        } catch (Exception e) {
            log.error("DashScope(OpenAI兼容)聊天异常 - sessionId: {}, 错误: {}",
                    request.getSessionId(), e.getMessage(), e);
            return LlmResponse.error("DASHSCOPE_ERROR", "DashScope API调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<LlmResponse> streamChat(LlmRequest request) {
        log.debug("DashScope(OpenAI兼容)流式聊天请求 - sessionId: {}, 模型: {}",
                request.getSessionId(), request.getModel());

        return chatClient.prompt()
                .messages(buildMessages(request))
                .options(buildOptions(request))
                .advisors(a -> a.param("chat_memory_conversation_id", request.getSessionId())
                        .param("chat_memory_response_size", 10))
                .stream()
                .content()
                .map(chunk -> LlmResponse.streamedChunk(chunk, false))
                .doOnError(e -> log.error("DashScope(OpenAI兼容)流式聊天异常: {}", e.getMessage(), e));
    }

    private OpenAiChatOptions buildOptions(LlmRequest request) {
        OpenAiChatOptions options = new OpenAiChatOptions();
        options.setModel(request.getModel());

        if (request.getTemperature() != null) {
            options.setTemperature(request.getTemperature().doubleValue());
        }
        if (request.getTopP() != null) {
            options.setTopP(request.getTopP().doubleValue());
        }
        if (request.getMaxTokens() != null) {
            options.setMaxTokens(request.getMaxTokens());
        }

        return options;
    }

    private List<Message> buildMessages(LlmRequest request) {
        List<LlmRequest.ChatMessage> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            log.error("DashScope buildMessages 失败: messages 为空");
            throw new IllegalArgumentException("messages 不能为空");
        }

        LlmRequest.ChatMessage firstMsg = messages.get(0);
        String content = firstMsg != null ? firstMsg.getContent() : null;

        if (content == null || content.isBlank()) {
            log.error("DashScope buildMessages 失败: 第一条消息 content 为空, content={}", content);
            throw new IllegalArgumentException("消息 content 不能为空");
        }

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            return List.of(
                    new SystemMessage(request.getSystemPrompt()),
                    new UserMessage(content)
            );
        }
        return List.of(new UserMessage(content));
    }
}
