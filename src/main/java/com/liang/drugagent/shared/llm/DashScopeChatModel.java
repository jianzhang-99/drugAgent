package com.liang.drugagent.shared.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 阿里云百炼(DashScope) ChatModel 实现 - Anthropic 兼容模式。
 *
 * <p>实现 Spring AI ChatModel 接口，接入百炼的 Anthropic 兼容端点。
 * 端点: https://dashscope.aliyuncs.com/apps/anthropic/v1/messages
 */
@Slf4j
@Component
public class DashScopeChatModel implements ChatModel {

    @Value("${spring.ai.dashscope.base-url:}")
    private String baseUrl;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${spring.ai.dashscope.chat.options.model:}")
    private String model;

    private RestClient restClient;

    private RestClient getRestClient() {
        if (restClient == null) {
            restClient = RestClient.builder()
                    .defaultHeaders(headers -> {
                        headers.set("Authorization", "Bearer " + apiKey);
                        headers.set("x-api-key", apiKey);
                        headers.set("anthropic-version", "2023-06-01");
                        headers.set("Content-Type", "application/json");
                    })
                    .build();
        }
        return restClient;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        log.debug("DashScope ChatModel 调用 - prompt: {}", prompt.getContents());

        try {
            List<Map<String, Object>> anthropicMessages = buildAnthropicMessages(prompt);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", anthropicMessages,
                    "max_tokens", 1024
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = getRestClient().post()
                    .uri(baseUrl + "/v1/messages")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            return toChatResponse(response);

        } catch (Exception e) {
            log.error("DashScope ChatModel 调用异常: {}", e.getMessage(), e);
            throw new RuntimeException("DashScope API 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 流式暂不支持，返回空流
        return Flux.empty();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder()
                .model(model)
                .build();
    }

    private List<Map<String, Object>> buildAnthropicMessages(Prompt prompt) {
        List<Map<String, Object>> result = new ArrayList<>();

        // 处理系统消息
        SystemMessage systemMessage = prompt.getSystemMessage();
        if (systemMessage != null && !systemMessage.getText().isBlank()) {
            result.add(Map.of(
                    "role", "user",
                    "content", systemMessage.getText()
            ));
        }

        // 处理用户消息
        List<UserMessage> userMessages = prompt.getUserMessages();
        if (userMessages != null && !userMessages.isEmpty()) {
            for (UserMessage msg : userMessages) {
                result.add(Map.of(
                        "role", msg.getMessageType().name().toLowerCase(),
                        "content", msg.getText()
                ));
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private ChatResponse toChatResponse(Map<String, Object> response) {
        if (response == null) {
            return ChatResponse.builder()
                    .generations(List.of(new Generation(new AssistantMessage(""))))
                    .build();
        }

        // 提取 content
        List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) response.get("content");
        String text = extractTextFromContent(contentBlocks);

        AssistantMessage assistantMessage = new AssistantMessage(text);

        // 提取 usage
        Usage usage = extractUsage(response);

        // 提取 model 和 id
        String modelId = (String) response.get("model");
        String responseId = (String) response.get("id");

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .id(responseId)
                .model(modelId != null ? modelId : this.model)
                .usage(usage)
                .build();

        return ChatResponse.builder()
                .generations(List.of(new Generation(assistantMessage)))
                .metadata(metadata)
                .build();
    }

    private String extractTextFromContent(List<Map<String, Object>> contentBlocks) {
        if (contentBlocks == null || contentBlocks.isEmpty()) {
            return "";
        }

        // 遍历 content 数组，找到 type=text 的元素
        for (Map<String, Object> block : contentBlocks) {
            if ("text".equals(block.get("type"))) {
                return (String) block.get("text");
            }
        }

        // 如果没有 text 类型，返回空字符串
        return "";
    }

    @SuppressWarnings("unchecked")
    private Usage extractUsage(Map<String, Object> response) {
        Map<String, Object> usageMap = (Map<String, Object>) response.get("usage");
        if (usageMap == null) {
            return new DefaultUsage(0, 0, 0);
        }

        int inputTokens = ((Number) usageMap.getOrDefault("input_tokens", 0)).intValue();
        int outputTokens = ((Number) usageMap.getOrDefault("output_tokens", 0)).intValue();
        int cacheCreationInputTokens = ((Number) usageMap.getOrDefault("cache_creation_input_tokens", 0)).intValue();
        int cacheReadInputTokens = ((Number) usageMap.getOrDefault("cache_read_input_tokens", 0)).intValue();

        return new DefaultUsage(
                inputTokens + cacheCreationInputTokens + cacheReadInputTokens,
                outputTokens,
                0
        );
    }
}
