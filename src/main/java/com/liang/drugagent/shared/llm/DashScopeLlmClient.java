package com.liang.drugagent.shared.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 阿里云百炼(DashScope) LLM 客户端 - Anthropic 兼容模式。
 *
 * <p>使用 Spring AI ChatClient + DashScopeChatModel 接入百炼。
 * 与 MiniMaxLlmClient 保持一致的代码结构。
 */
@Slf4j
@Component
public class DashScopeLlmClient implements LlmClient {

    private final ChatClient chatClient;

    public DashScopeLlmClient(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
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
        log.debug("DashScope(Anthropic兼容)聊天请求 - sessionId: {}, 模型: {}",
                request.getSessionId(), request.getModel());

        try {
            String response = chatClient.prompt()
                    .messages(buildMessages(request))
                    .advisors(a -> a.param("chat_memory_conversation_id", request.getSessionId())
                            .param("chat_memory_response_size", 10))
                    .call()
                    .content();

            log.debug("DashScope(Anthropic兼容)聊天响应 - sessionId: {}, 响应长度: {}",
                    request.getSessionId(), response.length());

            return LlmResponse.success(response, LlmProviderType.DASHSCOPE, request.getModel());
        } catch (Exception e) {
            log.error("DashScope(Anthropic兼容)聊天异常 - sessionId: {}, 错误: {}",
                    request.getSessionId(), e.getMessage(), e);
            return LlmResponse.error("DASHSCOPE_ERROR", "DashScope API调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<LlmResponse> streamChat(LlmRequest request) {
        log.debug("DashScope(Anthropic兼容)流式聊天请求 - sessionId: {}, 模型: {}",
                request.getSessionId(), request.getModel());

        return chatClient.prompt()
                .messages(buildMessages(request))
                .advisors(a -> a.param("chat_memory_conversation_id", request.getSessionId())
                        .param("chat_memory_response_size", 10))
                .stream()
                .content()
                .map(chunk -> LlmResponse.streamedChunk(chunk, false))
                .doOnError(e -> log.error("DashScope(Anthropic兼容)流式聊天异常: {}", e.getMessage(), e));
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
}
