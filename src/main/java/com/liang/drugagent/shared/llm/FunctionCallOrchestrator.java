package com.liang.drugagent.shared.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Function Calling 编排器。
 *
 * <p>负责管理可用工具、执行工具调用循环、
 * 将工具结果注入消息上下文并继续 LLM 对话。</p>
 *
 * <p>典型调用流程：
 * <pre>
 * 1. LLM 返回 tool_call → 解析工具名和参数
 * 2. 根据工具名找到对应 ToolExecutor
 * 3. 执行工具获取结果
 * 4. 将工具结果作为 assistant 消息的 tool_call 和 user 的 tool_result 追加到上下文
 * 5. 继续调用 LLM 直到模型不再调用工具
 * 6. 返回最终文本响应
 * </pre>
 * </p>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Slf4j
@Component
public class FunctionCallOrchestrator {

    private final Map<String, ToolExecutor> executorMap;
    private final LlmService llmService;

    public FunctionCallOrchestrator(List<ToolExecutor> executors, LlmService llmService) {
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(
                        ToolExecutor::getToolName,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
        this.llmService = llmService;
        log.info("FunctionCallOrchestrator 初始化 - 注册工具数: {}", executorMap.size());
    }

    /**
     * 执行带工具调用的对话。
     *
     * @param request  LLM 请求（已包含工具 schema）
     * @param maxIterations 最大工具调用轮次（防止无限循环）
     * @return 最终 LLM 响应（文本内容）
     */
    public LlmResponse execute(LlmRequest request, int maxIterations) {
        String sessionId = request.getSessionId();
        log.info("[{}] Function Calling 开始 - 工具数: {}, 最大迭代: {}",
                sessionId, request.getTools() != null ? request.getTools().size() : 0, maxIterations);

        // 深拷贝初始消息列表，避免修改原始请求
        java.util.ArrayList<LlmRequest.ChatMessage> chatMessages = new java.util.ArrayList<>();
        if (request.getMessages() != null) {
            chatMessages.addAll(request.getMessages());
        }

        LlmResponse response = callLlmWithTools(request, chatMessages);

        int iteration = 0;
        while (response.getToolCall() != null && iteration < maxIterations) {
            iteration++;
            log.info("[{}] 工具调用第 {} 轮 - 工具: {}", sessionId, iteration, response.getToolCall().getName());

            // 执行工具
            String toolResult = executeTool(response.getToolCall(), sessionId);

            // 将 LLM 的 tool_call 转换为 LlmRequest.ToolCall 并作为 assistant 消息
            LlmResponse.ToolCallResult tcResult = response.getToolCall();
            LlmRequest.ToolCall toolCall = LlmRequest.ToolCall.builder()
                    .id(tcResult.getId())
                    .name(tcResult.getName())
                    .arguments(tcResult.getArguments())
                    .build();

            chatMessages.add(LlmRequest.ChatMessage.builder()
                    .role("assistant")
                    .toolCall(toolCall)
                    .build());

            // 将工具执行结果作为 user 消息
            chatMessages.add(LlmRequest.ChatMessage.builder()
                    .role("user")
                    .content(toolResult)
                    .build());

            // 继续调用 LLM
            response = callLlmWithTools(request, chatMessages);
        }

        if (iteration >= maxIterations) {
            log.warn("[{}] 工具调用达到最大迭代次数 {}，终止", sessionId, maxIterations);
        }

        log.info("[{}] Function Calling 结束 - 总迭代: {}, 最终响应长度: {}",
                sessionId, iteration,
                response.getContent() != null ? response.getContent().length() : 0);

        return response;
    }

    /**
     * 构建工具 schema 列表。
     *
     * @return ToolDefinition 列表
     */
    public List<ToolDefinition> buildToolSchemas() {
        return executorMap.values().stream()
                .map(executor -> ToolDefinition.builder()
                        .type("function")
                        .function(ToolDefinition.Function.builder()
                                .name(executor.getToolName())
                                .description(executor.getDescription())
                                .parameters(buildParametersSchema(executor))
                                .build())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 为指定工具构建参数 schema（默认空 schema，子类可覆盖）。
     */
    protected Map<String, Object> buildParametersSchema(ToolExecutor executor) {
        return Map.of(
                "type", "object",
                "properties", new HashMap<String, Object>(),
                "required", new String[]{}
        );
    }

    private LlmResponse callLlmWithTools(LlmRequest request, List<LlmRequest.ChatMessage> messages) {
        LlmRequest updatedRequest = LlmRequest.builder()
                .provider(request.getProvider())
                .model(request.getModel())
                .systemPrompt(request.getSystemPrompt())
                .messages(messages)
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .topP(request.getTopP())
                .stream(false)
                .sessionId(request.getSessionId())
                .extraParams(request.getExtraParams())
                .tools(request.getTools())
                .build();

        return llmService.chat(updatedRequest);
    }

    private String executeTool(LlmResponse.ToolCallResult toolCall, String sessionId) {
        String toolName = toolCall.getName();
        ToolExecutor executor = executorMap.get(toolName);

        if (executor == null) {
            log.error("[{}] 未找到工具执行器: {}", sessionId, toolName);
            return "{\"error\": \"未找到工具: " + toolName + "\"}";
        }

        try {
            String result = executor.execute(toolCall.getParsedArguments(), sessionId);
            log.info("[{}] 工具执行成功 - tool: {}, 结果长度: {}", sessionId, toolName, result.length());
            return result;
        } catch (Exception e) {
            log.error("[{}] 工具执行异常 - tool: {}, 错误: {}", sessionId, toolName, e.getMessage(), e);
            return "{\"error\": \"工具执行失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 检查工具是否已注册。
     */
    public boolean isToolRegistered(String toolName) {
        return executorMap.containsKey(toolName);
    }

    /**
     * 获取已注册的工具名称列表。
     */
    public List<String> getRegisteredTools() {
        return List.copyOf(executorMap.keySet());
    }
}
