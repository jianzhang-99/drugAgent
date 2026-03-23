package com.liang.drugagent.llm;

import com.liang.drugagent.llm.model.LlmProviderType;
import com.liang.drugagent.llm.model.LlmRequest;
import com.liang.drugagent.llm.model.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 百炼（DashScope）LLM客户端实现
 *
 * 基于Spring AI Alibaba的ChatClient封装，提供与Spring AI标准接口对齐的LLM调用能力。
 * 支持百炼平台所有模型（如qwen-plus、qwen-max等）
 *
 * @author liangjiajian
 */
@Component
public class DashScopeLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(DashScopeLlmClient.class);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public DashScopeLlmClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new SimpleLoggerAdvisor()
                )
                .build();
    }

    @Override
    public LlmProviderType getProvider() {
        return LlmProviderType.DASHSCOPE;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        log.debug("DashScope chat request - sessionId: {}, model: {}",
                request.getSessionId(), request.getModel());

        try {
            String response = chatClient.prompt()
                    .messages(buildMessages(request))
                    .advisors(a -> a.param("chat_memory_conversation_id", request.getSessionId())
                            .param("chat_memory_response_size", 10))
                    .call()
                    .content();

            log.debug("DashScope chat response - sessionId: {}, response length: {}",
                    request.getSessionId(), response.length());

            return LlmResponse.success(response, LlmProviderType.DASHSCOPE, request.getModel());
        } catch (Exception e) {
            log.error("DashScope chat error - sessionId: {}, error: {}",
                    request.getSessionId(), e.getMessage(), e);
            return LlmResponse.error("DASHSCOPE_ERROR", "百炼API调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<LlmResponse> streamChat(LlmRequest request) {
        log.debug("DashScope stream chat request - sessionId: {}, model: {}",
                request.getSessionId(), request.getModel());

        return chatClient.prompt()
                .messages(buildMessages(request))
                .advisors(a -> a.param("chat_memory_conversation_id", request.getSessionId())
                        .param("chat_memory_response_size", 10))
                .stream()
                .content()
                .map(chunk -> LlmResponse.streamedChunk(chunk, false))
                .doOnError(e -> log.error("DashScope stream error: {}", e.getMessage(), e));
    }

    private List<Message> buildMessages(LlmRequest request) {
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            return List.of(
                    new SystemMessage(request.getSystemPrompt()),
                    new UserMessage(request.getMessages().get(0).getContent())
            );
        }
        return List.of(new UserMessage(request.getMessages().get(0).getContent()));
    }

    public String chat(String userMessage, String systemPrompt, String sessionId) {
        LlmRequest request = LlmRequest.builder()
                .sessionId(sessionId)
                .systemPrompt(systemPrompt)
                .messages(List.of(LlmRequest.ChatMessage.builder()
                        .role("user")
                        .content(userMessage)
                        .build()))
                .build();
        LlmResponse response = chat(request);
        if (Boolean.TRUE.equals(response.getSuccess())) {
            return response.getContent();
        }
        throw new RuntimeException(response.getErrorMessage());
    }

    public Flux<String> streamChat(String userMessage, String systemPrompt, String sessionId) {
        LlmRequest request = LlmRequest.builder()
                .sessionId(sessionId)
                .systemPrompt(systemPrompt)
                .messages(List.of(LlmRequest.ChatMessage.builder()
                        .role("user")
                        .content(userMessage)
                        .build()))
                .build();
        return streamChat(request).map(LlmResponse::getContent);
    }
}
