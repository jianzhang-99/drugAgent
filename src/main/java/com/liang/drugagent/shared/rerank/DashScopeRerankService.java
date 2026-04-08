package com.liang.drugagent.shared.rerank;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 阿里云百炼 Rerank 服务。
 *
 * <p>使用 DashScope 官方 Rerank API 对文档列表进行相关性排序。
 * 支持 qwen-rerank 等模型。</p>
 *
 * <p>使用方式：
 * <pre>
 * RerankRequest request = RerankRequest.builder()
 *         .query("如何申请药品注册")
 *         .documents(List.of("文档1内容", "文档2内容"))
 *         .topN(5)
 *         .build();
 * RerankResponse response = rerankService.rerank(request);
 * </pre>
 * </p>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Slf4j
@Service
public class DashScopeRerankService {

    private final String apiKey;
    private final String defaultModel;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DashScopeRerankService(
            @Value("${aliyun.dashscope.api-key:}") String apiKey,
            @Value("${aliyun.dashscope.rerank-model:qwen-rerank}") String defaultModel,
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
     * 执行文档重排序。
     *
     * @param request Rerank 请求
     * @return Rerank 响应
     */
    public RerankResponse rerank(RerankRequest request) {
        long startTime = System.currentTimeMillis();
        String model = defaultModel;

        log.info("[Rerank] 开始文档排序 - 模型: {}, 文档数: {}, 查询: {}",
                model,
                request.getDocuments() != null ? request.getDocuments().size() : 0,
                truncate(request.getQuery(), 50));

        try {
            String requestBody = buildRequestBody(request, model);
            String responseBody = callRerankApi(requestBody);

            RerankResponse response = parseResponse(responseBody, model);
            response.setCostMs(System.currentTimeMillis() - startTime);

            log.info("[Rerank] 文档排序完成 - 耗时: {}ms, 返回结果数: {}",
                    response.getCostMs(),
                    response.getResults() != null ? response.getResults().size() : 0);

            return response;

        } catch (Exception e) {
            log.error("[Rerank] 文档排序异常 - 错误: {}", e.getMessage(), e);
            return RerankResponse.error("RERANK_ERROR", "Rerank 调用失败: " + e.getMessage());
        }
    }

    /**
     * 执行文档重排序（简化版，直接返回排序后的索引）。
     *
     * @param query    查询文本
     * @param documents 文档列表
     * @param topN     返回数量
     * @return 排序后的文档索引列表（按相关性从高到低）
     */
    public List<Integer> rerank(String query, List<String> documents, int topN) {
        RerankRequest request = RerankRequest.builder()
                .query(query)
                .documents(documents)
                .topN(topN)
                .build();

        RerankResponse response = rerank(request);

        if (Boolean.TRUE.equals(response.getSuccess()) && response.getResults() != null) {
            return response.getResults().stream()
                    .map(RerankResponse.RerankResult::getIndex)
                    .toList();
        }

        // 降级：返回原始顺序
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            indices.add(i);
        }
        return indices;
    }

    private String buildRequestBody(RerankRequest request, String model) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", model);

        Map<String, Object> input = new java.util.HashMap<>();
        input.put("query", request.getQuery());
        input.put("documents", request.getDocuments());
        body.put("input", input);

        if (request.getTopN() != null) {
            body.put("top_n", request.getTopN());
        }

        return objectMapper.writeValueAsString(body);
    }

    private String callRerankApi(String requestBody) throws Exception {
        String url = baseUrl + "/api/v1/services/rerank/rerank";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Rerank API 返回错误: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    @SuppressWarnings("unchecked")
    private RerankResponse parseResponse(String responseBody, String model) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // 检查 API 错误
        if (root.has("error")) {
            JsonNode error = root.get("error");
            String code = error.has("code") ? error.get("code").asText() : "UNKNOWN";
            String message = error.has("message") ? error.get("message").asText() : "Unknown error";
            return RerankResponse.error(code, message);
        }

        List<RerankResponse.RerankResult> results = new ArrayList<>();

        JsonNode output = root.get("output");
        if (output != null && output.has("results")) {
            JsonNode resultsNode = output.get("results");
            if (resultsNode != null && resultsNode.isArray()) {
                for (JsonNode item : resultsNode) {
                    int index = item.has("index") ? item.get("index").asInt() : 0;
                    double score = item.has("relevance_score") ? item.get("relevance_score").asDouble() : 0.0;
                    String content = item.has("content") ? item.get("content").asText() : "";

                    results.add(RerankResponse.RerankResult.builder()
                            .index(index)
                            .relevanceScore(score)
                            .content(content)
                            .build());
                }
            }
        }

        // 按相关性得分排序
        results.sort((a, b) -> Double.compare(
                b.getRelevanceScore() != null ? b.getRelevanceScore() : 0,
                a.getRelevanceScore() != null ? a.getRelevanceScore() : 0
        ));

        return RerankResponse.success(results, model);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
