package com.liang.drugagent.shared.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 百炼批量推理服务。
 *
 * <p>批量推理用于离线处理大量请求，成本可降到实时推理的 50%。
 *
 * <p>适用场景：
 * <ul>
 *   <li>离线标书批量审查</li>
 *   <li>存量合同批处理</li>
 *   <li>规则验证集与评测集跑批</li>
 * </ul>
 * </p>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Slf4j
@Service
public class DashScopeBatchService {

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DashScopeBatchService(
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
     * 创建并提交批量推理任务。
     *
     * @param request 批量推理请求
     * @return 提交结果（包含 batchId）
     */
    public BatchInferenceResponse submitBatch(BatchInferenceRequest request) {
        log.info("[Batch] 提交批量任务 - name={}, itemCount={}",
                request.getName(), request.getItems() != null ? request.getItems().size() : 0);

        try {
            // 构建批量输入 JSONL
            String inputJsonl = buildInputJsonl(request);

            // 创建批量任务
            String batchId = createBatch(inputJsonl, request);

            log.info("[Batch] 批量任务提交成功 - batchId={}", batchId);

            return BatchInferenceResponse.builder()
                    .success(true)
                    .batchId(batchId)
                    .status("pending")
                    .totalCount(request.getItems() != null ? request.getItems().size() : 0)
                    .build();

        } catch (Exception e) {
            log.error("[Batch] 批量任务提交失败: {}", e.getMessage(), e);
            return BatchInferenceResponse.error("BATCH_SUBMIT_ERROR", "批量任务提交失败: " + e.getMessage());
        }
    }

    /**
     * 查询批量任务状态。
     *
     * @param batchId 批量任务 ID
     * @return 任务状态
     */
    public BatchInferenceResponse getBatchStatus(String batchId) {
        log.info("[Batch] 查询批量任务状态 - batchId={}", batchId);

        try {
            String url = baseUrl + "/api/v1/batches/" + batchId;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("查询批量任务状态失败: " + response.statusCode() + " - " + response.body());
            }

            return parseBatchStatusResponse(response.body(), batchId);

        } catch (Exception e) {
            log.error("[Batch] 查询批量任务状态失败 - batchId={}: {}", batchId, e.getMessage(), e);
            return BatchInferenceResponse.error("BATCH_STATUS_ERROR", "查询批量任务状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取批量任务结果。
     *
     * @param batchId 批量任务 ID
     * @return 批量结果
     */
    public BatchInferenceResponse getBatchResults(String batchId) {
        log.info("[Batch] 获取批量任务结果 - batchId={}", batchId);

        try {
            // 先查询状态
            BatchInferenceResponse statusResponse = getBatchStatus(batchId);
            if (!statusResponse.isSuccess() || !"completed".equals(statusResponse.getStatus())) {
                return statusResponse;
            }

            // 获取结果文件
            String resultContent = downloadResults(batchId);

            return parseBatchResults(resultContent, batchId);

        } catch (Exception e) {
            log.error("[Batch] 获取批量任务结果失败 - batchId={}: {}", batchId, e.getMessage(), e);
            return BatchInferenceResponse.error("BATCH_RESULTS_ERROR", "获取批量任务结果失败: " + e.getMessage());
        }
    }

    /**
     * 取消批量任务。
     *
     * @param batchId 批量任务 ID
     * @return 是否取消成功
     */
    public boolean cancelBatch(String batchId) {
        log.info("[Batch] 取消批量任务 - batchId={}", batchId);

        try {
            String url = baseUrl + "/api/v1/batches/" + batchId + "/cancel";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() >= 200 && response.statusCode() < 300;

        } catch (Exception e) {
            log.error("[Batch] 取消批量任务失败 - batchId={}: {}", batchId, e.getMessage(), e);
            return false;
        }
    }

    // ==================== Private Methods ====================

    private String buildInputJsonl(BatchInferenceRequest request) throws Exception {
        StringBuilder jsonl = new StringBuilder();
        for (BatchInferenceRequest.BatchItem item : request.getItems()) {
            ObjectNode inputItem = objectMapper.createObjectNode();
            inputItem.put("custom_id", item.getId());

            ObjectNode messageItem = objectMapper.createObjectNode();
            messageItem.put("role", "user");

            ArrayNode contentArray = objectMapper.createArrayNode();
            ObjectNode textContent = objectMapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", item.getPrompt());
            contentArray.add(textContent);
            messageItem.set("content", contentArray);

            inputItem.set("message", messageItem);

            if (item.getSystemPrompt() != null) {
                ObjectNode systemItem = objectMapper.createObjectNode();
                systemItem.put("role", "system");
                ObjectNode systemContent = objectMapper.createObjectNode();
                systemContent.put("type", "text");
                systemContent.put("text", item.getSystemPrompt());
                systemItem.set("content", systemContent);
                inputItem.set("system", systemItem);
            }

            ObjectNode parameters = objectMapper.createObjectNode();
            parameters.put("model", request.getModel());
            if (item.getTemperature() != null) {
                parameters.put("temperature", item.getTemperature());
            }
            if (item.getMaxTokens() != null) {
                parameters.put("max_tokens", item.getMaxTokens());
            }
            inputItem.set("parameters", parameters);

            jsonl.append(objectMapper.writeValueAsString(inputItem)).append("\n");
        }
        return jsonl.toString();
    }

    private String createBatch(String inputJsonl, BatchInferenceRequest request) throws Exception {
        String url = baseUrl + "/api/v1/batches";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", request.getModel());
        body.put("input_file_content", Base64.getEncoder().encodeToString(inputJsonl.getBytes(StandardCharsets.UTF_8)));

        if (request.getName() != null) {
            body.put("name", request.getName());
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("创建批量任务失败: " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.has("id") ? root.get("id").asText() : null;
    }

    private BatchInferenceResponse parseBatchStatusResponse(String responseBody, String batchId) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        String status = root.has("status") ? root.get("status").asText() : "unknown";
        Integer completedCount = root.has("completed_count") ? root.get("completed_count").asInt() : 0;
        Integer failedCount = root.has("failed_count") ? root.get("failed_count").asInt() : 0;
        Integer totalCount = root.has("total_count") ? root.get("total_count").asInt() : 0;

        return BatchInferenceResponse.builder()
                .success(true)
                .batchId(batchId)
                .status(status)
                .completedCount(completedCount)
                .failedCount(failedCount)
                .totalCount(totalCount)
                .build();
    }

    private String downloadResults(String batchId) throws Exception {
        // 首先获取 output_file_id
        String statusUrl = baseUrl + "/api/v1/batches/" + batchId;
        HttpRequest statusRequest = HttpRequest.newBuilder()
                .uri(URI.create(statusUrl))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> statusResponse = httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode statusRoot = objectMapper.readTree(statusResponse.body());

        String outputFileId = statusRoot.has("output_file_id") ? statusRoot.get("output_file_id").asText() : null;
        if (outputFileId == null) {
            throw new RuntimeException("批量任务未完成，无法获取结果");
        }

        // 下载结果文件
        String downloadUrl = baseUrl + "/api/v1/files/" + outputFileId + "/content";
        HttpRequest downloadRequest = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> downloadResponse = httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofString());

        if (downloadResponse.statusCode() >= 400) {
            throw new RuntimeException("下载批量结果失败: " + downloadResponse.statusCode());
        }

        return downloadResponse.body();
    }

    private BatchInferenceResponse parseBatchResults(String resultContent, String batchId) throws Exception {
        List<BatchInferenceResponse.BatchResult> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        String[] lines = resultContent.split("\n");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }

            JsonNode resultNode = objectMapper.readTree(line);
            String customId = resultNode.has("custom_id") ? resultNode.get("custom_id").asText() : "";

            BatchInferenceResponse.BatchResult.BatchResultBuilder resultBuilder = BatchInferenceResponse.BatchResult.builder()
                    .id(customId);

            if (resultNode.has("error")) {
                resultBuilder.success(false);
                resultBuilder.errorCode(resultNode.get("error").has("code") ?
                        resultNode.get("error").get("code").asText() : "UNKNOWN");
                resultBuilder.errorMessage(resultNode.get("error").has("message") ?
                        resultNode.get("error").get("message").asText() : "Unknown error");
                failCount++;
            } else {
                resultBuilder.success(true);

                JsonNode output = resultNode.get("output");
                if (output != null && output.has("choices")) {
                    JsonNode choices = output.get("choices");
                    if (choices != null && choices.isArray() && choices.size() > 0) {
                        JsonNode firstChoice = choices.get(0);
                        JsonNode message = firstChoice.get("message");
                        if (message != null && message.has("content")) {
                            // content 是数组，需要提取 text
                            JsonNode content = message.get("content");
                            if (content.isArray()) {
                                StringBuilder textBuilder = new StringBuilder();
                                for (JsonNode contentItem : content) {
                                    if (contentItem.has("text")) {
                                        textBuilder.append(contentItem.get("text").asText());
                                    }
                                }
                                resultBuilder.content(textBuilder.toString());
                            } else {
                                resultBuilder.content(content.asText());
                            }
                        }
                        resultBuilder.finishReason(firstChoice.has("finish_reason") ?
                                firstChoice.get("finish_reason").asText() : null);
                    }
                }
                successCount++;
            }

            results.add(resultBuilder.build());
        }

        return BatchInferenceResponse.builder()
                .success(true)
                .batchId(batchId)
                .status("completed")
                .results(results)
                .completedCount(successCount)
                .failedCount(failCount)
                .totalCount(results.size())
                .build();
    }
}
