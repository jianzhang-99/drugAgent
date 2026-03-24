package com.liang.drugagent.thirdparty.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.shared.llm.LlmClient;
import com.liang.drugagent.shared.llm.model.LlmProviderType;
import com.liang.drugagent.shared.llm.model.LlmRequest;
import com.liang.drugagent.shared.llm.model.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * MiniMax LLM客户端实现
 *
 * 基于HTTP封装的MiniMax API调用，支持：
 * - Bearer Token认证
 * - 流式输出（SSE）
 * - MiniMax Chat Completion API
 *
 * @author liangjiajian
 */
@Component
public class MiniMaxLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MiniMaxLlmClient.class);

    private static final String CHAT_COMPLETION_PATH = "/v1/text/chatcompletion_v2";

    private final MiniMaxProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public MiniMaxLlmClient(MiniMaxProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public LlmProviderType getProvider() {
        return LlmProviderType.MINIMAX;
    }

    @Override
    public boolean isAvailable() {
        return properties.isEnabled() &&
               properties.getApiKey() != null &&
               !properties.getApiKey().isBlank();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        log.debug("MiniMax chat request - userMessage length: {}",
                request.getMessages().get(0).getContent().length());

        try {
            MiniMaxRequest mmRequest = buildRequest(request, false);
            String responseBody = webClient.post()
                    .uri(CHAT_COMPLETION_PATH)
                    .header("Authorization", "Bearer " + generateAuthToken())
                    .bodyValue(mmRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(properties.getTimeout()))
                    .block();

            String content = parseNonStreamResponse(responseBody);
            return LlmResponse.success(content, LlmProviderType.MINIMAX, request.getModel());
        } catch (WebClientResponseException e) {
            log.error("MiniMax API error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return LlmResponse.error("MINIMAX_API_ERROR",
                    "MiniMax API调用失败: " + e.getStatusCode() + " - " + e.getMessage());
        } catch (Exception e) {
            log.error("MiniMax chat error: {}", e.getMessage(), e);
            return LlmResponse.error("MINIMAX_ERROR", "MiniMax API调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<LlmResponse> streamChat(LlmRequest request) {
        log.debug("MiniMax stream chat request - userMessage length: {}",
                request.getMessages().get(0).getContent().length());

        try {
            MiniMaxRequest mmRequest = buildRequest(request, true);

            return webClient.post()
                    .uri(CHAT_COMPLETION_PATH)
                    .header("Authorization", "Bearer " + generateAuthToken())
                    .bodyValue(mmRequest)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofMillis(properties.getTimeout()))
                    .flatMap(line -> parseSseLine(line));
        } catch (Exception e) {
            log.error("MiniMax stream chat error: {}", e.getMessage(), e);
            return Flux.error(new RuntimeException("MiniMax流式API调用失败: " + e.getMessage(), e));
        }
    }

    private Flux<LlmResponse> parseSseLine(String line) {
        if (line.startsWith("data: ")) {
            String jsonStr = line.substring(6).trim();
            if ("[DONE]".equals(jsonStr)) {
                return Flux.empty();
            }
            try {
                JsonNode node = objectMapper.readTree(jsonStr);
                JsonNode choices = node.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    String content = choices.get(0).path("delta").path("content").asText("");
                    if (!content.isEmpty()) {
                        return Flux.just(LlmResponse.streamedChunk(content, false));
                    }
                }
            } catch (Exception e) {
                log.warn("解析MiniMax SSE行失败: {}", line);
            }
        }
        return Flux.empty();
    }

    private MiniMaxRequest buildRequest(LlmRequest request, boolean stream) {
        MiniMaxRequest mmRequest = new MiniMaxRequest();
        mmRequest.setModel(request.getModel() != null ? request.getModel() : properties.getModel());
        mmRequest.setStream(stream);
        mmRequest.setMax_tokens(request.getMaxTokens() != null ? request.getMaxTokens() : properties.getMaxTokens());
        mmRequest.setTemperature(request.getTemperature() != null ? request.getTemperature() : properties.getTemperature());

        List<MiniMaxMessage> messages = new ArrayList<>();

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            MiniMaxMessage systemMsg = new MiniMaxMessage();
            systemMsg.setRole("system");
            systemMsg.setContent(request.getSystemPrompt());
            messages.add(systemMsg);
        }

        for (LlmRequest.ChatMessage msg : request.getMessages()) {
            MiniMaxMessage mmMsg = new MiniMaxMessage();
            mmMsg.setRole(msg.getRole());
            mmMsg.setContent(msg.getContent());
            messages.add(mmMsg);
        }

        mmRequest.setMessages(messages);
        return mmRequest;
    }

    private String generateAuthToken() {
        // MiniMax使用Bearer Token认证，直接返回API Key
        return properties.getApiKey();
    }

    private String parseNonStreamResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
            throw new RuntimeException("MiniMax响应格式异常: 未找到choices");
        } catch (Exception e) {
            log.error("解析MiniMax响应失败: {}", responseBody);
            throw new RuntimeException("解析MiniMax响应失败: " + e.getMessage(), e);
        }
    }

    // ==================== 内部类：请求模型 ====================

    public static class MiniMaxRequest {
        private String model;
        private List<MiniMaxMessage> messages;
        private boolean stream;
        private int max_tokens;
        private float temperature;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public List<MiniMaxMessage> getMessages() { return messages; }
        public void setMessages(List<MiniMaxMessage> messages) { this.messages = messages; }
        public boolean isStream() { return stream; }
        public void setStream(boolean stream) { this.stream = stream; }
        public int getMax_tokens() { return max_tokens; }
        public void setMax_tokens(int max_tokens) { this.max_tokens = max_tokens; }
        public float getTemperature() { return temperature; }
        public void setTemperature(float temperature) { this.temperature = temperature; }
    }

    public static class MiniMaxMessage {
        private String role;
        private String content;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
