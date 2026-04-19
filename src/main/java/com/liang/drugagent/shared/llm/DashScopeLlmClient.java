package com.liang.drugagent.shared.llm;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/**
 * 阿里云百炼(DashScope) LLM 客户端 - 官方 SDK 模式。
 *
 * <p>使用 DashScope 官方 Java SDK 直接调用原生 API，
 * 与阿里云文档保持一致，便于使用原生能力和排查兼容性问题。
 * 支持 Function Calling（通过 Generation API）、JSON Schema 结构化输出等高级特性。</p>
 */
@Slf4j
@Component
public class DashScopeLlmClient implements LlmClient {

    private final String apiKey;
    private final String defaultModel;
    private final ObjectMapper objectMapper;
    private final Generation generation;

    public DashScopeLlmClient(
            @Value("${aliyun.dashscope.api-key:}") String apiKey,
            @Value("${aliyun.dashscope.chat-model:qwen3.5-plus}") String defaultModel) {
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.objectMapper = new ObjectMapper();
        this.generation = new Generation();
    }

    @Override
    public LlmProviderType getProvider() {
        return LlmProviderType.DASHSCOPE;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        String model = resolveModel(request);
        log.debug("DashScope(官方SDK)聊天请求 - sessionId: {}, 模型: {}, 工具数: {}, 响应格式: {}",
                request.getSessionId(), model,
                request.getTools() != null ? request.getTools().size() : 0,
                request.getResponseFormat());

        // 统一使用 Generation API（支持 Function Calling 和 JSON Schema）
        return chatWithGeneration(request, model);
    }

    /**
     * 使用 Generation API 进行聊天（支持 Function Calling 和 JSON Schema）。
     */
    private LlmResponse chatWithGeneration(LlmRequest request, String model) {
        try {
            // 构建消息
            List<Message> messages = buildMessages(request);

            // 构建 GenerationParam
            GenerationParam.GenerationParamBuilder paramBuilder = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .messages(messages);

            // 设置工具（Function Calling）
            if (request.getTools() != null && !request.getTools().isEmpty()) {
                List<FunctionDefinition> tools = buildTools(request.getTools());
                paramBuilder.tools(tools);
                paramBuilder.resultFormat(GenerationParam.ResultFormat.MESSAGE);
            }

            // 设置温度
            if (request.getTemperature() != null) {
                paramBuilder.temperature(request.getTemperature());
            }

            // 设置最大 token
            if (request.getMaxTokens() != null) {
                paramBuilder.maxTokens(request.getMaxTokens());
            }
            applyExtraParams(paramBuilder, request.getExtraParams());

            // 设置响应格式
            if (request.getResponseFormat() != null && !request.getResponseFormat().isBlank()) {
                setResponseFormat(paramBuilder, request.getResponseFormat());
            }

            // 结构化输出场景需关闭思考模式，避免百炼报错
            if (Boolean.FALSE.equals(request.getThinkingEnabled())) {
                paramBuilder.parameter("enable_thinking", false);
            } else if (Boolean.TRUE.equals(request.getThinkingEnabled()) && supportsThinkingMode(model)) {
                paramBuilder.parameter("enable_thinking", true);
            }

            GenerationParam param = paramBuilder.build();

            // 调用
            GenerationResult result = generation.call(param);

            // 提取结果
            return extractResult(result, model);

        } catch (Exception e) {
            log.error("Function Calling 调用失败 - sessionId: {}", request.getSessionId(), e);
            return LlmResponse.error("FUNCTION_CALL_ERROR", "Function Calling 调用失败: " + e.getMessage());
        }
    }

    private List<FunctionDefinition> buildTools(List<ToolDefinition> toolDefs) {
        List<FunctionDefinition> tools = new ArrayList<>();
        for (ToolDefinition toolDef : toolDefs) {
            if (toolDef.getFunction() != null) {
                // 将 Map 转换为 JsonObject
                JsonObject parameters = convertToJsonObject(toolDef.getFunction().getParameters());
                FunctionDefinition func = FunctionDefinition.builder()
                        .name(toolDef.getFunction().getName())
                        .description(toolDef.getFunction().getDescription())
                        .parameters(parameters)
                        .build();
                tools.add(func);
            }
        }
        return tools;
    }

    private JsonObject convertToJsonObject(Map<String, Object> map) {
        JsonObject jsonObject = new JsonObject();
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                jsonObject.add(entry.getKey(), convertToJsonElement(entry.getValue()));
            }
        }
        return jsonObject;
    }

    @SuppressWarnings("unchecked")
    private JsonElement convertToJsonElement(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof String stringValue) {
            return new com.google.gson.JsonPrimitive(stringValue);
        }
        if (value instanceof Number numberValue) {
            return new com.google.gson.JsonPrimitive(numberValue);
        }
        if (value instanceof Boolean booleanValue) {
            return new com.google.gson.JsonPrimitive(booleanValue);
        }
        if (value instanceof Map<?, ?> mapValue) {
            return convertToJsonObject((Map<String, Object>) mapValue);
        }
        if (value instanceof List<?> listValue) {
            JsonArray jsonArray = new JsonArray();
            for (Object item : listValue) {
                jsonArray.add(convertToJsonElement(item));
            }
            return jsonArray;
        }
        try {
            return JsonParser.parseString(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            log.warn("对象转 JsonElement 失败: {}", e.getMessage());
            return JsonNull.INSTANCE;
        }
    }

    private List<Message> buildMessages(LlmRequest request) {
        List<Message> sdkMessages = new ArrayList<>();

        // 添加系统提示
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            Message sysMsg = Message.builder()
                    .role("system")
                    .content(request.getSystemPrompt())
                    .build();
            sdkMessages.add(sysMsg);
        }

        List<LlmRequest.ChatMessage> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            // 如果没有消息但有 systemPrompt，用它作为用户消息
            if (!sdkMessages.isEmpty()) {
                return sdkMessages;
            }
            throw new IllegalArgumentException("messages 不能为空");
        }

        for (LlmRequest.ChatMessage message : messages) {
            if (message == null) continue;

            if (message.getToolCall() != null) {
                // 工具调用消息 - 使用 toolCallId
                Message toolMsg = Message.builder()
                        .role("assistant")
                        .toolCallId(message.getToolCall().getId())
                        .content("")
                        .build();
                sdkMessages.add(toolMsg);
                continue;
            }

            if (message.getContent() == null || message.getContent().isBlank()) continue;

            Message userMsg = Message.builder()
                    .role("user")
                    .content(message.getContent())
                    .build();
            sdkMessages.add(userMsg);
        }

        return sdkMessages;
    }

    private void setResponseFormat(GenerationParam.GenerationParamBuilder paramBuilder, String responseFormat) {
        // responseFormat 格式：json_object 或 json_schema:{response_format_json}
        if ("json_object".equalsIgnoreCase(responseFormat)) {
            paramBuilder.resultFormat(GenerationParam.ResultFormat.MESSAGE);
            paramBuilder.parameter("response_format", Map.of("type", "json_object"));
        } else if (responseFormat.startsWith("json_schema:")) {
            String schemaContent = responseFormat.substring("json_schema:".length());
            try {
                Map<String, Object> format = objectMapper.readValue(schemaContent, Map.class);
                if (!format.containsKey("type")) {
                    format = Map.of(
                            "type", "json_schema",
                            "json_schema", format
                    );
                }
                paramBuilder.resultFormat(GenerationParam.ResultFormat.MESSAGE);
                paramBuilder.parameter("response_format", format);
            } catch (JsonProcessingException e) {
                log.warn("解析 JSON Schema 失败: {}", e.getMessage());
            }
        }
    }

    private LlmResponse extractResult(GenerationResult result, String model) {
        if (result == null) {
            return LlmResponse.error("NO_RESULT", "Generation 返回为空");
        }

        GenerationOutput output = result.getOutput();
        if (output == null) {
            return LlmResponse.error("NO_OUTPUT", "Generation 返回 output 为空");
        }

        // 优先从 output.getText() 获取内容
        String content = output.getText();
        if (content == null || content.isBlank()) {
            // 如果没有 text，尝试从 choices 获取
            List<GenerationOutput.Choice> choices = output.getChoices();
            if (choices != null && !choices.isEmpty()) {
                GenerationOutput.Choice firstChoice = choices.get(0);
                if (firstChoice != null) {
                    Message msg = firstChoice.getMessage();
                    if (msg != null) {
                        content = msg.getContent();
                    }
                }
            }
        }

        String reasoningContent = extractReasoningContent(output);

        // 尝试获取工具调用
        LlmResponse.ToolCallResult toolCall = null;
        try {
            List<GenerationOutput.Choice> choices = output.getChoices();
            if (choices != null && !choices.isEmpty()) {
                GenerationOutput.Choice firstChoice = choices.get(0);
                if (firstChoice != null) {
                    Message msg = firstChoice.getMessage();
                    if (msg != null && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                        // 获取第一个工具调用
                        ToolCallBase toolCallBase = msg.getToolCalls().get(0);
                        if (toolCallBase instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> toolCallMap = (Map<String, Object>) toolCallBase;
                            String name = (String) toolCallMap.get("name");
                            Object argumentsObj = toolCallMap.get("arguments");
                            String arguments = argumentsObj != null ? argumentsObj.toString() : "{}";
                            Map<String, Object> parsedArgs = parseArguments(arguments);
                            toolCall = LlmResponse.ToolCallResult.builder()
                                    .id(UUID.randomUUID().toString())
                                    .name(name)
                                    .arguments(arguments)
                                    .parsedArguments(parsedArgs)
                                    .build();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.trace("获取工具调用失败", e);
        }

        LlmResponse.LlmResponseBuilder builder = LlmResponse.builder()
                .success(true)
                .content(content != null ? content : "")
                .reasoningContent(reasoningContent)
                .model(model)
                .provider(LlmProviderType.DASHSCOPE);

        if (toolCall != null) {
            builder.toolCall(toolCall);
        }

        return builder.build();
    }

    private void applyExtraParams(GenerationParam.GenerationParamBuilder paramBuilder, Map<String, Object> extraParams) {
        if (paramBuilder == null || extraParams == null || extraParams.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : extraParams.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            if (entry.getValue() == null) {
                continue;
            }
            paramBuilder.parameter(entry.getKey(), entry.getValue());
        }
    }

    private void applyThinkingParameters(GenerationParam.GenerationParamBuilder paramBuilder,
                                         LlmRequest request,
                                         String model) {
        if (paramBuilder == null || request == null) {
            return;
        }
        if (Boolean.FALSE.equals(request.getThinkingEnabled())) {
            paramBuilder.parameter("enable_thinking", false);
            return;
        }
        if (Boolean.TRUE.equals(request.getThinkingEnabled()) && supportsThinkingMode(model)) {
            paramBuilder.parameter("enable_thinking", true);
        }
    }

    private boolean supportsThinkingMode(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String lower = model.toLowerCase(Locale.ROOT);
        return lower.startsWith("qwen3")
                || lower.startsWith("qwq")
                || lower.startsWith("deepseek-r1");
    }

    private String extractReasoningContent(GenerationOutput output) {
        try {
            if (output == null) {
                return "";
            }
            List<GenerationOutput.Choice> choices = output.getChoices();
            if (choices == null || choices.isEmpty()) {
                return "";
            }
            GenerationOutput.Choice firstChoice = choices.get(0);
            if (firstChoice == null) {
                return "";
            }
            Message msg = firstChoice.getMessage();
            if (msg == null) {
                return "";
            }
            return msg.getReasoningContent() != null ? msg.getReasoningContent() : "";
        } catch (Exception e) {
            log.trace("提取 reasoning_content 失败", e);
            return "";
        }
    }

    @Override
    public Flux<LlmResponse> streamChat(LlmRequest request) {
        String model = resolveModel(request);
        log.debug("DashScope(官方SDK)流式聊天请求 - sessionId: {}, 模型: {}, 工具数: {}, 响应格式: {}",
                request.getSessionId(), model,
                request.getTools() != null ? request.getTools().size() : 0,
                request.getResponseFormat());

        // 如果有工具或需要 JSON Schema 输出，降级到同步调用
        if (request.getTools() != null && !request.getTools().isEmpty()
                || request.getResponseFormat() != null && !request.getResponseFormat().isBlank()) {
            return Flux.defer(() -> {
                LlmResponse resp = chatWithGeneration(request, model);
                return Flux.just(resp);
            });
        }

        // 使用 Generation API 流式调用
        return Flux.defer(() -> {
            try {
                List<Message> messages = buildMessages(request);

                GenerationParam.GenerationParamBuilder paramBuilder = GenerationParam.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .messages(messages)
                        .incrementalOutput(true);

                if (request.getTemperature() != null) {
                    paramBuilder.temperature(request.getTemperature());
                }
                if (request.getMaxTokens() != null) {
                    paramBuilder.maxTokens(request.getMaxTokens());
                }

                applyExtraParams(paramBuilder, request.getExtraParams());
                applyThinkingParameters(paramBuilder, request, model);

                GenerationParam param = paramBuilder.build();

                Flowable<GenerationResult> stream = generation.streamCall(param);

                return Flux.from(stream)
                        .map(result -> {
                            String chunk = extractChunkText(result);
                            String reasoningChunk = extractReasoningContent(result != null ? result.getOutput() : null);
                            boolean isLast = isLastChunk(result);

                            return LlmResponse.builder()
                                    .success(true)
                                    .content(chunk)
                                    .reasoningContent(reasoningChunk)
                                    .model(model)
                                    .provider(LlmProviderType.DASHSCOPE)
                                    .streamed(true)
                                    .isLast(isLast)
                                    .finishReason(isLast ? "stop" : null)
                                    .build();
                        })
                        .filter(resp -> (resp.getContent() != null && !resp.getContent().isEmpty())
                                || (resp.getReasoningContent() != null && !resp.getReasoningContent().isEmpty())
                                || Boolean.TRUE.equals(resp.getIsLast()))
                        .doOnError(e -> log.error("DashScope(官方SDK)流式聊天异常 - sessionId: {}, 错误: {}",
                                request.getSessionId(), e.getMessage(), e));
            } catch (Exception e) {
                log.error("DashScope(官方SDK)流式聊天初始化失败 - sessionId: {}, 错误: {}",
                        request.getSessionId(), e.getMessage(), e);
                return Flux.error(new RuntimeException("DashScope 流式调用失败: " + e.getMessage(), e));
            }
        });
    }

    private String extractChunkText(GenerationResult result) {
        try {
            GenerationOutput output = result.getOutput();
            if (output == null) return "";

            // 优先从 output.getText() 获取内容
            String text = output.getText();
            if (text != null && !text.isBlank()) {
                return text;
            }

            // 如果没有 text，尝试从 choices 获取
            List<GenerationOutput.Choice> choices = output.getChoices();
            if (choices == null || choices.isEmpty()) return "";

            GenerationOutput.Choice firstChoice = choices.get(0);
            if (firstChoice == null) return "";

            Message message = firstChoice.getMessage();
            if (message == null) return "";

            return message.getContent() != null ? message.getContent() : "";
        } catch (Exception e) {
            log.trace("提取流式chunk文本失败", e);
            return "";
        }
    }

    private boolean isLastChunk(GenerationResult result) {
        try {
            GenerationOutput output = result.getOutput();
            if (output == null) return false;

            List<GenerationOutput.Choice> choices = output.getChoices();
            if (choices == null || choices.isEmpty()) return false;

            GenerationOutput.Choice firstChoice = choices.get(0);
            String finishReason = firstChoice.getFinishReason();
            return finishReason != null && !finishReason.isBlank() && !"null".equalsIgnoreCase(finishReason);
        } catch (Exception e) {
            log.trace("检查流式chunk是否完成失败", e);
            return false;
        }
    }

    private String resolveModel(LlmRequest request) {
        return request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel()
                : defaultModel;
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析工具参数失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
