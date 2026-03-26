package com.liang.drugagent.thirdparty.llm;

import com.liang.drugagent.shared.llm.LlmClient;
import com.liang.drugagent.shared.llm.LlmProviderType;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmResponse;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
public class DashScopeLlmClient implements LlmClient {

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
        log.debug("百炼聊天请求 - sessionId: {}, 模型: {}",
                request.getSessionId(), request.getModel());

        try {
            String response = chatClient.prompt()
                    .messages(buildMessages(request))
                    .advisors(a -> a.param("chat_memory_conversation_id", request.getSessionId())
                            .param("chat_memory_response_size", 10))
                    .call()
                    .content();

            log.debug("百炼聊天响应 - sessionId: {}, 响应长度: {}",
                    request.getSessionId(), response.length());

            return LlmResponse.success(response, LlmProviderType.DASHSCOPE, request.getModel());
        } catch (Exception e) {
            log.error("百炼聊天异常 - sessionId: {}, 错误: {}",
                    request.getSessionId(), e.getMessage(), e);
            return LlmResponse.error("DASHSCOPE_ERROR", "百炼API调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<LlmResponse> streamChat(LlmRequest request) {
        log.debug("百炼流式聊天请求 - sessionId: {}, 模型: {}",
                request.getSessionId(), request.getModel());

        return chatClient.prompt()
                .messages(buildMessages(request))
                .advisors(a -> a.param("chat_memory_conversation_id", request.getSessionId())
                        .param("chat_memory_response_size", 10))
                .stream()
                .content()
                .map(chunk -> LlmResponse.streamedChunk(chunk, false))
                .doOnError(e -> log.error("百炼流式聊天异常: {}", e.getMessage(), e));
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
