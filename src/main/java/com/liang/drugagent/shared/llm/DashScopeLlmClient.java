package com.liang.drugagent.shared.llm;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里云百炼(DashScope) LLM 客户端 - 官方 SDK 模式。
 *
 * <p>使用 DashScope 官方 Java SDK 直接调用原生 API，
 * 与阿里云文档保持一致，便于使用原生能力和排查兼容性问题。
 */
@Slf4j
@Component
public class DashScopeLlmClient implements LlmClient {

    private final String apiKey;
    private final String defaultModel;

    public DashScopeLlmClient(
            @Value("${aliyun.dashscope.api-key:}") String apiKey,
            @Value("${aliyun.dashscope.chat-model:qwen3.5-plus}") String defaultModel) {
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
    }

    @Override
    public LlmProviderType getProvider() {
        return LlmProviderType.DASHSCOPE;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        String model = resolveModel(request);
        log.debug("DashScope(官方SDK)聊天请求 - sessionId: {}, 模型: {}",
                request.getSessionId(), model);

        try {
            MultiModalConversation conversation = new MultiModalConversation();
            MultiModalConversationResult result = conversation.call(buildParam(request, model, false));
            String content = extractText(result);

            log.debug("DashScope(官方SDK)聊天响应 - sessionId: {}, 响应长度: {}",
                    request.getSessionId(), content != null ? content.length() : 0);

            return LlmResponse.success(content, LlmProviderType.DASHSCOPE, model);
        } catch (ApiException | NoApiKeyException | UploadFileException e) {
            log.error("DashScope(官方SDK)聊天异常 - sessionId: {}, 错误: {}",
                    request.getSessionId(), e.getMessage(), e);
            return LlmResponse.error("DASHSCOPE_ERROR", "DashScope API调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<LlmResponse> streamChat(LlmRequest request) {
        String model = resolveModel(request);
        log.debug("DashScope(官方SDK)流式聊天请求 - sessionId: {}, 模型: {}",
                request.getSessionId(), model);

        return Flux.defer(() -> {
            try {
                MultiModalConversation conversation = new MultiModalConversation();
                Flowable<MultiModalConversationResult> stream = conversation.streamCall(buildParam(request, model, true));
                return Flux.from(stream)
                        .map(result -> {
                            String chunk = extractText(result);
                            boolean isLast = hasFinishReason(result);
                            return LlmResponse.builder()
                                    .success(true)
                                    .content(chunk)
                                    .model(model)
                                    .provider(LlmProviderType.DASHSCOPE)
                                    .streamed(true)
                                    .isLast(isLast)
                                    .finishReason(extractFinishReason(result))
                                    .build();
                        })
                        .filter(resp -> (resp.getContent() != null && !resp.getContent().isEmpty()) || Boolean.TRUE.equals(resp.getIsLast()))
                        .doOnError(e -> log.error("DashScope(官方SDK)流式聊天异常 - sessionId: {}, 错误: {}",
                                request.getSessionId(), e.getMessage(), e));
            } catch (ApiException | NoApiKeyException | UploadFileException e) {
                log.error("DashScope(官方SDK)流式聊天初始化失败 - sessionId: {}, 错误: {}",
                        request.getSessionId(), e.getMessage(), e);
                return Flux.error(new RuntimeException("DashScope 流式调用失败: " + e.getMessage(), e));
            }
        });
    }

    private String resolveModel(LlmRequest request) {
        return request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel()
                : defaultModel;
    }

    private MultiModalConversationParam buildParam(LlmRequest request, String model, boolean stream) {
        MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(buildMessages(request))
                .incrementalOutput(stream);

        if (request.getTopP() != null) {
            builder.topP(request.getTopP().doubleValue());
        }
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        }

        return builder.build();
    }

    private List<Object> buildMessages(LlmRequest request) {
        List<LlmRequest.ChatMessage> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            log.error("DashScope buildMessages 失败: messages 为空");
            throw new IllegalArgumentException("messages 不能为空");
        }

        List<Object> sdkMessages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            sdkMessages.add(MultiModalMessage.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(buildTextContent(request.getSystemPrompt()))
                    .build());
        }

        for (LlmRequest.ChatMessage message : messages) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }

            String role = "assistant".equalsIgnoreCase(message.getRole())
                    ? Role.ASSISTANT.getValue()
                    : Role.USER.getValue();

            sdkMessages.add(MultiModalMessage.builder()
                    .role(role)
                    .content(buildTextContent(message.getContent()))
                    .build());
        }

        if (sdkMessages.isEmpty()) {
            throw new IllegalArgumentException("消息 content 不能为空");
        }
        return sdkMessages;
    }

    private List<Map<String, Object>> buildTextContent(String text) {
        Map<String, Object> item = new HashMap<>();
        item.put("text", text);
        return List.of(item);
    }

    private String extractText(MultiModalConversationResult result) {
        if (result == null || result.getOutput() == null || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            return "";
        }

        MultiModalMessage message = result.getOutput().getChoices().get(0).getMessage();
        if (message == null || message.getContent() == null || message.getContent().isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> item : message.getContent()) {
            Object text = item.get("text");
            if (text != null) {
                builder.append(text);
            }
        }
        return builder.toString();
    }

    private boolean hasFinishReason(MultiModalConversationResult result) {
        String finishReason = extractFinishReason(result);
        return finishReason != null
                && !finishReason.isBlank()
                && !"null".equalsIgnoreCase(finishReason);
    }

    private String extractFinishReason(MultiModalConversationResult result) {
        if (result == null || result.getOutput() == null) {
            return null;
        }

        MultiModalConversationOutput output = result.getOutput();
        if (output.getChoices() != null && !output.getChoices().isEmpty()) {
            return output.getChoices().get(0).getFinishReason();
        }
        return output.getFinishReason();
    }
}
