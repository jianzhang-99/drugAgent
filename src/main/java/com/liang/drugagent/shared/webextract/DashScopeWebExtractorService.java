package com.liang.drugagent.shared.webextract;

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
import java.util.Map;

/**
 * 百炼 Web Extractor 服务。
 *
 * <p>Web Extractor 用于从网页中提取结构化内容。
 * 模型配合网页抓取进行网页内容读取，适合：
 * <ul>
 *   <li>抓取公开监管公告</li>
 *   <li>抓取招采公告</li>
 *   <li>抓取政策文件网页正文</li>
 * </ul>
 * </p>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Slf4j
@Service
public class DashScopeWebExtractorService {

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DashScopeWebExtractorService(
            @Value("${aliyun.dashscope.api-key:}") String apiKey,
            @Value("${aliyun.dashscope.base-url:https://dashscope.aliyuncs.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 从网页中提取内容。
     *
     * @param request 网页提取请求
     * @return 提取结果
     */
    public WebExtractResponse extract(WebExtractRequest request) {
        log.info("[WebExtractor] 开始提取网页 - url={}, prompt={}",
                request.getUrl(), request.getPrompt() != null ? request.getPrompt().substring(0, Math.min(50, request.getPrompt().length())) + "..." : "null");

        try {
            String requestBody = buildRequestBody(request);
            String responseBody = callWebExtractorApi(requestBody);

            return parseResponse(responseBody, request.getUrl());

        } catch (Exception e) {
            log.error("[WebExtractor] 网页提取失败 - url={}: {}", request.getUrl(), e.getMessage(), e);
            return WebExtractResponse.error("WEB_EXTRACT_ERROR", "网页提取失败: " + e.getMessage());
        }
    }

    /**
     * 构建请求体。
     */
    private String buildRequestBody(WebExtractRequest request) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : "qwen-plus");

        // 构建 input
        Map<String, Object> input = new HashMap<>();
        input.put("url", request.getUrl());
        if (request.getPrompt() != null) {
            input.put("prompt", request.getPrompt());
        }

        body.put("input", input);

        return objectMapper.writeValueAsString(body);
    }

    /**
     * 调用 Web Extractor API。
     */
    private String callWebExtractorApi(String requestBody) throws Exception {
        String url = baseUrl + "/api/v1/feeds/extract";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Web Extractor API 返回错误: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    /**
     * 解析响应。
     */
    private WebExtractResponse parseResponse(String responseBody, String originalUrl) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        if (root.has("error")) {
            JsonNode error = root.get("error");
            String code = error.has("code") ? error.get("code").asText() : "UNKNOWN";
            String message = error.has("message") ? error.get("message").asText() : "Unknown error";
            return WebExtractResponse.error(code, message);
        }

        String content = "";
        String title = null;

        JsonNode output = root.get("output");
        if (output != null) {
            // 尝试从 output 中提取内容
            if (output.has("content")) {
                content = output.get("content").asText();
            } else if (output.has("result")) {
                content = output.get("result").asText();
            } else if (output.has("text")) {
                content = output.get("text").asText();
            }

            if (output.has("title")) {
                title = output.get("title").asText();
            }
        }

        // 如果没有提取到内容，尝试从 fulltext 或其他字段获取
        if (content.isEmpty() && root.has("output")) {
            JsonNode outputNode = root.get("output");
            if (outputNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : outputNode) {
                    if (item.has("content")) {
                        sb.append(item.get("content").asText()).append("\n");
                    }
                }
                content = sb.toString().trim();
            }
        }

        return WebExtractResponse.builder()
                .success(true)
                .content(content)
                .title(title)
                .url(originalUrl)
                .build();
    }
}
