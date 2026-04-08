package com.liang.drugagent.shared.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 百炼 Responses API 服务。
 *
 * <p>Responses API 是百炼提供的另一种 API 风格，
 * 支持更灵活的对话管理和自动上下文注入。
 *
 * <p>与 Chat API 的区别：
 * <ul>
 *   <li>Responses API 自动管理对话历史，无需手动拼接 messages</li>
 *   <li>支持 attachments（附件）直接传入</li>
 *   <li>支持与 Tools/MCP 配合使用</li>
 * </ul>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Slf4j
@Service
public class DashScopeResponsesService {

    private final String apiKey;
    private final String defaultModel;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DashScopeResponsesService(
            @Value("${aliyun.dashscope.api-key:}") String apiKey,
            @Value("${aliyun.dashscope.chat-model:qwen3.5-plus}") String defaultModel,
            @Value("${aliyun.dashscope.base-url:https://dashscope.aliyuncs.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 发送对话请求（使用 Responses API）。
     *
     * @param request LLM 请求
     * @return LLM 响应
     */
    public LlmResponse chat(LlmRequest request) {
        long startTime = System.currentTimeMillis();
        String model = request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel() : defaultModel;

        log.info("[Responses API] 开始对话 - 模型: {}, sessionId: {}",
                model, request.getSessionId());

        try {
            String requestBody = buildRequestBody(request, model);
            String responseBody = callResponsesApi(requestBody);

            LlmResponse response = parseResponse(responseBody, model);
            log.info("[Responses API] 对话完成 - 耗时: {}ms, 文本长度: {}",
                    System.currentTimeMillis() - startTime,
                    response.getContent() != null ? response.getContent().length() : 0);

            return response;

        } catch (Exception e) {
            log.error("[Responses API] 对话异常 - 错误: {}", e.getMessage(), e);
            return LlmResponse.error("RESPONSES_API_ERROR", "Responses API 调用失败: " + e.getMessage());
        }
    }

    /**
     * 构建请求体。
     */
    @SuppressWarnings("unchecked")
    private String buildRequestBody(LlmRequest request, String model) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);

        // 构建 input
        Map<String, Object> input = new HashMap<>();

        // 构建消息内容
        StringBuilder prompt = new StringBuilder();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            prompt.append(request.getSystemPrompt()).append("\n\n");
        }

        List<LlmRequest.ChatMessage> messages = request.getMessages();
        if (messages != null) {
            for (LlmRequest.ChatMessage msg : messages) {
                if (msg == null) continue;
                if (msg.getContent() != null && !msg.getContent().isBlank()) {
                    prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
                }
            }
        }
        input.put("prompt", prompt.toString().trim());

        // 添加附件（如果有）
        // attachments 格式: [{type, data}]
        // 暂不实现，基于实际需求

        body.put("input", input);

        // 添加工具（如果有）
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", convertTools(request.getTools()));
        }

        // 添加参数
        Map<String, Object> parameters = new HashMap<>();
        if (request.getTemperature() != null) {
            parameters.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            parameters.put("max_tokens", request.getMaxTokens());
        }
        if (!parameters.isEmpty()) {
            body.put("parameters", parameters);
        }

        return objectMapper.writeValueAsString(body);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertTools(List<ToolDefinition> tools) {
        return tools.stream().map(tool -> {
            Map<String, Object> result = new HashMap<>();
            result.put("type", "function");
            if (tool.getFunction() != null) {
                result.put("name", tool.getFunction().getName());
                result.put("description", tool.getFunction().getDescription());
                result.put("parameters", tool.getFunction().getParameters());
            }
            return result;
        }).toList();
    }

    /**
     * 调用 Responses API。
     */
    private String callResponsesApi(String requestBody) throws Exception {
        String url = baseUrl + "/api/v1/responses";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Responses API 返回错误: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    /**
     * 解析响应。
     */
    private LlmResponse parseResponse(String responseBody, String model) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // 检查错误
        if (root.has("error")) {
            JsonNode error = root.get("error");
            String code = error.has("code") ? error.get("code").asText() : "UNKNOWN";
            String message = error.has("message") ? error.get("message").asText() : "Unknown error";
            return LlmResponse.error(code, message);
        }

        String content = "";
        LlmResponse.ToolCallResult toolCall = null;

        JsonNode output = root.get("output");
        if (output != null) {
            JsonNode choices = output.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.get("message");
                if (message != null) {
                    JsonNode contentNode = message.get("content");
                    if (contentNode != null) {
                        content = contentNode.asText();
                    }

                    // 解析工具调用
                    JsonNode toolCallNode = message.get("tool_calls");
                    if (toolCallNode != null && toolCallNode.isArray() && toolCallNode.size() > 0) {
                        JsonNode firstToolCall = toolCallNode.get(0);
                        String name = firstToolCall.has("name") ? firstToolCall.get("name").asText() : "";
                        String arguments = firstToolCall.has("arguments") ?
                                (firstToolCall.get("arguments").isObject() ?
                                        objectMapper.writeValueAsString(firstToolCall.get("arguments")) :
                                        firstToolCall.get("arguments").asText()) : "{}";

                        toolCall = LlmResponse.ToolCallResult.builder()
                                .id(java.util.UUID.randomUUID().toString())
                                .name(name)
                                .arguments(arguments)
                                .parsedArguments(parseArguments(arguments))
                                .build();
                    }
                }
            }
        }

        LlmResponse.LlmResponseBuilder builder = LlmResponse.builder()
                .success(true)
                .content(content)
                .model(model)
                .provider(LlmProviderType.DASHSCOPE);

        if (toolCall != null) {
            builder.toolCall(toolCall);
        }

        return builder.build();
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(arguments, java.util.Map.class);
        } catch (Exception e) {
            log.warn("[Responses API] 解析工具参数失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
