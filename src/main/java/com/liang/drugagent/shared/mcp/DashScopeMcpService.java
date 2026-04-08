package com.liang.drugagent.shared.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmResponse;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.shared.llm.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 百炼 MCP (Model Context Protocol) 服务。
 *
 * <p>MCP 允许通过 Responses API 配置外部工具服务器，
 * 让模型能够直接调用外部工具与服务。</p>
 *
 * <p>支持的 MCP Server 类型：
 * <ul>
 *   <li>搜索服务 (search)</li>
 *   <li>代码执行 (code_interpreter)</li>
 *   <li>万相绘图 (Wanx)</li>
 *   <li>自定义 MCP Server</li>
 * </ul>
 * </p>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Slf4j
@Service
public class DashScopeMcpService {

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LlmService llmService;

    /**
     * 已注册的 MCP Server 配置
     */
    private final Map<String, McpConfig.McpServer> registeredServers = new HashMap<>();

    public DashScopeMcpService(
            @Value("${aliyun.dashscope.api-key:}") String apiKey,
            @Value("${aliyun.dashscope.base-url:https://dashscope.aliyuncs.com}") String baseUrl,
            LlmService llmService) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.llmService = llmService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 注册 MCP Server。
     *
     * @param server MCP Server 配置
     */
    public void registerServer(McpConfig.McpServer server) {
        log.info("[MCP] 注册 Server: name={}, type={}", server.getName(), server.getType());
        registeredServers.put(server.getName(), server);
    }

    /**
     * 注销 MCP Server。
     *
     * @param serverName Server 名称
     */
    public void unregisterServer(String serverName) {
        log.info("[MCP] 注销 Server: {}", serverName);
        registeredServers.remove(serverName);
    }

    /**
     * 获取已注册的 Server 列表。
     */
    public List<McpConfig.McpServer> getRegisteredServers() {
        return new ArrayList<>(registeredServers.values());
    }

    /**
     * 执行带 MCP 工具的对话。
     *
     * <p>此方法将 MCP Server 配置转换为工具定义，
     * 并通过 Responses API 调用百炼模型。</p>
     *
     * @param request LLM 请求
     * @return LLM 响应
     */
    public LlmResponse chatWithMcp(LlmRequest request) {
        log.info("[MCP] 开始 MCP 对话 - sessionId={}, servers={}",
                request.getSessionId(), registeredServers.size());

        try {
            // 将 MCP Server 转换为工具定义
            List<ToolDefinition> tools = convertMcpServersToTools();

            // 添加工具到请求
            if (tools != null && !tools.isEmpty()) {
                if (request.getTools() == null) {
                    request.setTools(tools);
                } else {
                    request.getTools().addAll(tools);
                }
            }

            // 通过 Responses API 调用
            String requestBody = buildMcpRequestBody(request);
            String responseBody = callMcpApi(requestBody);

            return parseMcpResponse(responseBody, request.getModel());

        } catch (Exception e) {
            log.error("[MCP] MCP 对话异常: {}", e.getMessage(), e);
            return LlmResponse.error("MCP_ERROR", "MCP 调用失败: " + e.getMessage());
        }
    }

    /**
     * 将 MCP Server 转换为工具定义。
     */
    private List<ToolDefinition> convertMcpServersToTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        for (McpConfig.McpServer server : registeredServers.values()) {
            if (server.getType() == null) {
                continue;
            }

            ToolDefinition tool = ToolDefinition.builder()
                    .type("function")
                    .function(ToolDefinition.Function.builder()
                            .name("mcp_" + server.getName())
                            .description("MCP Server [" + server.getName() + "] - Type: " + server.getType())
                            .parameters(buildToolParameters(server))
                            .build())
                    .build();
            tools.add(tool);
        }

        return tools;
    }

    /**
     * 构建工具参数 Schema。
     */
    private Map<String, Object> buildToolParameters(McpConfig.McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        // 根据 Server 类型添加不同的参数
        switch (server.getType().toLowerCase()) {
            case "search":
                properties.put("query", Map.of(
                        "type", "string",
                        "description", "搜索查询词"
                ));
                properties.put("topK", Map.of(
                        "type", "integer",
                        "description", "返回结果数量"
                ));
                break;
            case "code_interpreter":
                properties.put("code", Map.of(
                        "type", "string",
                        "description", "要执行的代码"
                ));
                break;
            case "web_extractor":
                properties.put("url", Map.of(
                        "type", "string",
                        "description", "网页 URL 地址"
                ));
                properties.put("prompt", Map.of(
                        "type", "string",
                        "description", "内容提取提示词，指定要从网页中提取的信息"
                ));
                break;
            default:
                properties.put("action", Map.of(
                        "type", "string",
                        "description", "操作类型"
                ));
                properties.put("params", Map.of(
                        "type", "object",
                        "description", "操作参数"
                ));
        }

        return Map.of(
                "type", "object",
                "properties", properties,
                "required", new String[]{"action"}
        );
    }

    /**
     * 构建 MCP 请求体。
     */
    @SuppressWarnings("unchecked")
    private String buildMcpRequestBody(LlmRequest request) throws Exception {
        Map<String, Object> body = new HashMap<>();
        String model = request.getModel() != null ? request.getModel() : "qwen3.5-plus";
        body.put("model", model);

        // 构建 input
        Map<String, Object> input = new HashMap<>();
        StringBuilder prompt = new StringBuilder();

        if (request.getSystemPrompt() != null) {
            prompt.append(request.getSystemPrompt()).append("\n\n");
        }

        if (request.getMessages() != null) {
            for (LlmRequest.ChatMessage msg : request.getMessages()) {
                if (msg != null && msg.getContent() != null) {
                    prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
                }
            }
        }
        input.put("prompt", prompt.toString().trim());

        // 添加 MCP Server 配置
        if (!registeredServers.isEmpty()) {
            List<Map<String, Object>> mcpServers = new ArrayList<>();
            for (McpConfig.McpServer server : registeredServers.values()) {
                Map<String, Object> serverConfig = new HashMap<>();
                serverConfig.put("type", server.getType());

                Map<String, Object> serverDetail = new HashMap<>();
                serverDetail.put("name", server.getName());
                if (server.getUrl() != null) {
                    serverDetail.put("url", server.getUrl());
                }
                if (server.getAuthType() != null) {
                    serverDetail.put("auth_type", server.getAuthType());
                }
                if (server.getAuthConfig() != null) {
                    serverDetail.put("auth_config", server.getAuthConfig());
                }

                serverConfig.put(server.getType(), serverDetail);
                mcpServers.add(serverConfig);
            }
            body.put("mcp_servers", mcpServers);
        }

        body.put("input", input);

        // 添加工具
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolDefinition tool : request.getTools()) {
                if (tool.getFunction() != null) {
                    tools.add(Map.of(
                            "type", "function",
                            "function", Map.of(
                                    "name", tool.getFunction().getName(),
                                    "description", tool.getFunction().getDescription(),
                                    "parameters", tool.getFunction().getParameters() != null ?
                                            tool.getFunction().getParameters() : Map.of()
                            )
                    ));
                }
            }
            body.put("tools", tools);
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

    /**
     * 调用 MCP API。
     */
    private String callMcpApi(String requestBody) throws Exception {
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
            throw new RuntimeException("MCP API 返回错误: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    /**
     * 解析 MCP 响应。
     */
    private LlmResponse parseMcpResponse(String responseBody, String model) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

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

                    JsonNode toolCallsNode = message.get("tool_calls");
                    if (toolCallsNode != null && toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                        JsonNode firstToolCall = toolCallsNode.get(0);
                        String name = firstToolCall.has("name") ? firstToolCall.get("name").asText() : "";
                        String arguments = "{}";
                        if (firstToolCall.has("arguments")) {
                            if (firstToolCall.get("arguments").isObject()) {
                                arguments = objectMapper.writeValueAsString(firstToolCall.get("arguments"));
                            } else {
                                arguments = firstToolCall.get("arguments").asText();
                            }
                        }

                        toolCall = LlmResponse.ToolCallResult.builder()
                                .id(UUID.randomUUID().toString())
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
                .model(model != null ? model : "qwen3.5-plus")
                .provider(com.liang.drugagent.shared.llm.LlmProviderType.DASHSCOPE);

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
            return objectMapper.readValue(arguments, Map.class);
        } catch (Exception e) {
            log.warn("[MCP] 解析工具参数失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
