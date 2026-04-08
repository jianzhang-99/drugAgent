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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public DashScopeLlmClient(
            @Value("${aliyun.dashscope.api-key:}") String apiKey,
            @Value("${aliyun.dashscope.chat-model:qwen3.5-plus}") String defaultModel) {
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.objectMapper = new ObjectMapper();
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

        // 如果有工具或需要 JSON Schema 输出，使用 Generation API
        if (request.getTools() != null && !request.getTools().isEmpty()
                || request.getResponseFormat() != null && !request.getResponseFormat().isBlank()) {
            return chatWithGeneration(request, model);
        }

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

    /**
     * 使用 Generation API 进行聊天（支持 Function Calling 和 JSON Schema）。
     */
    private LlmResponse chatWithGeneration(LlmRequest request, String model) {
        try {
            // 动态加载 Generation API 类（避免编译时依赖不存在的类）
            Class<?> generationClass = Class.forName("com.alibaba.dashscope.aigc.generation.Generation");
            Class<?> generationParamClass = Class.forName("com.alibaba.dashscope.aigc.generation.GenerationParam");
            Class<?> functionClass = Class.forName("com.alibaba.dashscope.tools.FunctionDefinition");

            Object generation = generationClass.getDeclaredConstructor().newInstance();

            // 构建工具列表
            List<Object> tools = buildToolsList(request.getTools(), functionClass);

            // 构建消息
            List<Object> messages = buildMessagesForGeneration(request);

            // 构建 GenerationParam
            Object param = buildGenerationParam(generationParamClass, model, messages, tools, request);

            // 调用
            Object result = generationClass.getMethod("call", generationParamClass).invoke(generation, param);

            // 提取结果
            return extractGenerationResult(result, model);

        } catch (ClassNotFoundException e) {
            log.error("Generation API 类未找到，请确认 DashScope SDK 版本支持 Function Calling", e);
            return LlmResponse.error("SDK_ERROR", "Function Calling API 不可用: " + e.getMessage());
        } catch (Exception e) {
            log.error("Function Calling 调用失败 - sessionId: {}", request.getSessionId(), e);
            return LlmResponse.error("FUNCTION_CALL_ERROR", "Function Calling 调用失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> buildToolsList(List<ToolDefinition> toolDefs, Class<?> functionClass) throws Exception {
        List<Object> tools = new ArrayList<>();
        for (ToolDefinition toolDef : toolDefs) {
            if (toolDef.getFunction() != null) {
                // 使用 FunctionDefinition.builder().name().description().parameters().build()
                Object funcBuilder = functionClass.getMethod("builder").invoke(null);
                funcBuilder.getClass().getMethod("name", String.class).invoke(funcBuilder, toolDef.getFunction().getName());
                funcBuilder.getClass().getMethod("description", String.class).invoke(funcBuilder, toolDef.getFunction().getDescription());
                funcBuilder.getClass().getMethod("parameters", Map.class).invoke(funcBuilder, toolDef.getFunction().getParameters());
                Object func = funcBuilder.getClass().getMethod("build").invoke(funcBuilder);
                tools.add(func);
            }
        }
        return tools;
    }

    private List<Object> buildMessagesForGeneration(LlmRequest request) throws Exception {
        List<LlmRequest.ChatMessage> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }

        List<Object> sdkMessages = new ArrayList<>();
        Class<?> messageClass = Class.forName("com.alibaba.dashscope.common.Message");
        Class<?> roleEnumClass = Class.forName("com.alibaba.dashscope.common.Role");

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            Object sysMsg = messageClass.getDeclaredConstructor().newInstance();
            messageClass.getMethod("role", roleEnumClass).invoke(sysMsg, roleEnumClass.getField("SYSTEM").get(null));
            messageClass.getMethod("content", String.class).invoke(sysMsg, request.getSystemPrompt());
            sdkMessages.add(sysMsg);
        }

        for (LlmRequest.ChatMessage message : messages) {
            if (message == null) continue;

            if (message.getToolCall() != null) {
                // 工具调用消息
                Object toolMsg = messageClass.getDeclaredConstructor().newInstance();
                messageClass.getMethod("role", roleEnumClass).invoke(toolMsg, roleEnumClass.getField("ASSISTANT").get(null));

                Map<String, Object> toolCallMap = new HashMap<>();
                toolCallMap.put("id", message.getToolCall().getId());
                toolCallMap.put("type", "function");
                Map<String, Object> funcMap = new HashMap<>();
                funcMap.put("name", message.getToolCall().getName());
                funcMap.put("arguments", message.getToolCall().getArguments());
                toolCallMap.put("function", funcMap);
                messageClass.getMethod("toolCall", Map.class).invoke(toolMsg, toolCallMap);
                sdkMessages.add(toolMsg);
                continue;
            }

            if (message.getContent() == null || message.getContent().isBlank()) continue;

            Object userMsg = messageClass.getDeclaredConstructor().newInstance();
            messageClass.getMethod("role", roleEnumClass).invoke(userMsg, roleEnumClass.getField("USER").get(null));
            messageClass.getMethod("content", String.class).invoke(userMsg, message.getContent());
            sdkMessages.add(userMsg);
        }

        return sdkMessages;
    }

    private Object buildGenerationParam(Class<?> paramClass, String model, List<Object> messages,
                                       List<Object> tools, LlmRequest request) throws Exception {
        Object builder = paramClass.getMethod("builder").invoke(null);
        builder.getClass().getMethod("model", String.class).invoke(builder, model);
        builder.getClass().getMethod("messages", List.class).invoke(builder, messages);
        if (tools != null && !tools.isEmpty()) {
            builder.getClass().getMethod("tools", List.class).invoke(builder, tools);
        }
        builder.getClass().getMethod("apiKey", String.class).invoke(builder, apiKey);

        if (request.getTemperature() != null) {
            builder.getClass().getMethod("temperature", Float.class).invoke(builder, request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            builder.getClass().getMethod("maxTokens", Integer.class).invoke(builder, request.getMaxTokens());
        }

        // 设置响应格式（JSON Schema / JSON Object）
        if (request.getResponseFormat() != null && !request.getResponseFormat().isBlank()) {
            setResponseFormat(builder, request.getResponseFormat());
        }

        // 设置思考模式（禁用思考可降低延迟和成本）
        if (request.getThinkingEnabled() != null && !request.getThinkingEnabled()) {
            setThinkingDisabled(builder);
        }

        return builder.getClass().getMethod("build").invoke(builder);
    }

    /**
     * 设置响应格式。
     */
    @SuppressWarnings("unchecked")
    private void setResponseFormat(Object builder, String responseFormat) throws Exception {
        Class<?> responseFormatClass = Class.forName("com.alibaba.dashscope.common.ResponseFormat");

        // responseFormat 格式：json_object 或 json_schema:{schema}
        if ("json_object".equalsIgnoreCase(responseFormat)) {
            Object rf = responseFormatClass.getMethod("builder").invoke(null);
            rf.getClass().getMethod("type", String.class).invoke(rf, "json_object");
            Object result = rf.getClass().getMethod("build").invoke(rf);
            builder.getClass().getMethod("responseFormat", responseFormatClass).invoke(builder, result);
        } else if (responseFormat.startsWith("json_schema:")) {
            // json_schema:{name}:{schema_json}
            String schemaContent = responseFormat.substring("json_schema:".length());
            Object rf = responseFormatClass.getMethod("builder").invoke(null);
            rf.getClass().getMethod("type", String.class).invoke(rf, "json_schema");
            // 解析 schema JSON
            Map<String, Object> schema = objectMapper.readValue(schemaContent, Map.class);
            rf.getClass().getMethod("jsonSchema", Map.class).invoke(rf, schema);
            Object result = rf.getClass().getMethod("build").invoke(rf);
            builder.getClass().getMethod("responseFormat", responseFormatClass).invoke(builder, result);
        }
    }

    /**
     * 设置禁用思考模式。
     * 通过 extraParams 传递 thinking.disable=true 参数。
     */
    private void setThinkingDisabled(Object builder) {
        try {
            // 尝试通过 extraParams 设置 thinking.disable
            // GenerationParam 有 extraParams(Map<String, Object>) 方法
            Map<String, Object> extraParams = new HashMap<>();
            extraParams.put("thinking", Map.of("type", "disabled"));
            builder.getClass().getMethod("extraParams", Map.class).invoke(builder, extraParams);
            log.debug("已设置禁用思考模式");
        } catch (Exception e) {
            log.warn("设置思考模式失败，可能 SDK 版本不支持: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private LlmResponse extractGenerationResult(Object result, String model) throws Exception {
        Class<?> outputClass = Class.forName("com.alibaba.dashscope.aigc.generation.GenerationOutput");

        Object output = result.getClass().getMethod("getOutput").invoke(result);
        if (output == null) {
            return LlmResponse.error("NO_OUTPUT", "Generation 返回 output 为空");
        }

        // 获取 choices
        List<Object> choices = (List<Object>) outputClass.getMethod("getChoices").invoke(output);
        if (choices == null || choices.isEmpty()) {
            return LlmResponse.error("NO_CHOICES", "Generation 返回 choices 为空");
        }

        Object firstChoice = choices.get(0);

        // 尝试获取文本内容
        String content = "";
        try {
            Object message = firstChoice.getClass().getMethod("getMessage").invoke(firstChoice);
            if (message != null) {
                Object msgContent = message.getClass().getMethod("getContent").invoke(message);
                if (msgContent != null) {
                    content = msgContent.toString();
                }
            }
        } catch (Exception e) {
            log.trace("获取文本内容失败", e);
        }

        // 尝试获取工具调用
        LlmResponse.ToolCallResult toolCall = null;
        try {
            Object fc = outputClass.getMethod("getFunctionCall").invoke(output);
            if (fc != null) {
                String name = (String) fc.getClass().getMethod("getName").invoke(fc);
                String arguments = fc.getClass().getMethod("getArguments").invoke(fc) != null
                        ? fc.getClass().getMethod("getArguments").invoke(fc).toString() : "{}";
                Map<String, Object> parsedArgs = parseArguments(arguments);
                toolCall = LlmResponse.ToolCallResult.builder()
                        .id(UUID.randomUUID().toString())
                        .name(name)
                        .arguments(arguments)
                        .parsedArguments(parsedArgs)
                        .build();
            }
        } catch (Exception e) {
            log.trace("获取工具调用失败", e);
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

    @Override
    public Flux<LlmResponse> streamChat(LlmRequest request) {
        String model = resolveModel(request);
        log.debug("DashScope(官方SDK)流式聊天请求 - sessionId: {}, 模型: {}, 工具数: {}, 响应格式: {}",
                request.getSessionId(), model,
                request.getTools() != null ? request.getTools().size() : 0,
                request.getResponseFormat());

        // 如果有工具或需要 JSON Schema 输出，降级到同步调用（Generation API 暂时不支持流式）
        if (request.getTools() != null && !request.getTools().isEmpty()
                || request.getResponseFormat() != null && !request.getResponseFormat().isBlank()) {
            return Flux.defer(() -> {
                LlmResponse resp = chatWithGeneration(request, model);
                return Flux.just(resp);
            });
        }

        return Flux.defer(() -> {
            try {
                MultiModalConversation conversation = new MultiModalConversation();
                Flowable<MultiModalConversationResult> stream = conversation.streamCall(buildParam(request, model, true));

                final StringBuilder fullContent = new StringBuilder();

                return Flux.from(stream)
                        .map(result -> {
                            String chunk = extractText(result);
                            if (chunk != null) {
                                fullContent.append(chunk);
                            }

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
                        .filter(resp -> (resp.getContent() != null && !resp.getContent().isEmpty())
                                || Boolean.TRUE.equals(resp.getIsLast()))
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
            if (message == null) {
                continue;
            }

            // 处理工具调用消息
            if (message.getToolCall() != null) {
                sdkMessages.add(buildToolMessage(message));
                continue;
            }

            String role = "assistant".equalsIgnoreCase(message.getRole())
                    ? Role.ASSISTANT.getValue()
                    : Role.USER.getValue();

            // 处理多模态内容（图片、视频等）
            if (message.getMultiModalContent() != null && !message.getMultiModalContent().isEmpty()) {
                sdkMessages.add(MultiModalMessage.builder()
                        .role(role)
                        .content(message.getMultiModalContent())
                        .build());
                continue;
            }

            // 处理纯文本内容
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }

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

    @SuppressWarnings("unchecked")
    private MultiModalMessage buildToolMessage(LlmRequest.ChatMessage message) {
        Map<String, Object> toolCallContent = new HashMap<>();
        toolCallContent.put("id", message.getToolCall().getId());
        toolCallContent.put("type", "function");
        toolCallContent.put("function", Map.of(
                "name", message.getToolCall().getName(),
                "arguments", message.getToolCall().getArguments()
        ));

        return MultiModalMessage.builder()
                .role(Role.ASSISTANT.getValue())
                .content(List.of(toolCallContent))
                .build();
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
            // 跳过 function_call 类型的内容
            if (item.containsKey("type") && "function_call".equals(item.get("type"))) {
                continue;
            }
            Object text = item.get("text");
            if (text != null) {
                builder.append(text);
            }
        }
        return builder.toString();
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
