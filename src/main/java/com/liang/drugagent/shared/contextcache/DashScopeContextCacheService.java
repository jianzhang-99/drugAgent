package com.liang.drugagent.shared.contextcache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 百炼 Context Cache 服务。
 *
 * <p>Context Cache 用于缓存对话上下文，
 * 减少重复的 token 计算，提升响应速度并降低成本。</p>
 *
 * <p>支持的缓存模式：
 * <ul>
 *   <li>显式缓存：手动创建和管理缓存</li>
 *   <li>隐式缓存：API 自动处理缓存</li>
 * </ul>
 * </p>
 *
 * <p>适用场景：
 * <ul>
 *   <li>多轮对话：同一会话的上下文复用</li>
 *   <li>长 System Prompt：公共提示词缓存</li>
 *   <li>同一文档多轮问答：文档内容缓存</li>
 * </ul>
 * </p>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Slf4j
@Service
public class DashScopeContextCacheService {

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 本地缓存的上下文缓存（sessionId -> cacheId）
     */
    private final Map<String, String> sessionCacheMap = new ConcurrentHashMap<>();

    /**
     * 记录每个 session 当前缓存对应的 prompt 指纹，避免复用过期的 RAG/摘要上下文。
     */
    private final Map<String, String> sessionPromptFingerprintMap = new ConcurrentHashMap<>();

    public DashScopeContextCacheService(
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
     * 创建显式上下文缓存。
     *
     * @param content 要缓存的内容
     * @param sessionId 会话 ID（用于关联）
     * @return 缓存配置
     */
    public ContextCacheConfig createCache(String content, String sessionId) {
        log.info("[ContextCache] 创建缓存 - sessionId={}, contentLength={}",
                sessionId, content != null ? content.length() : 0);

        try {
            String requestBody = buildCreateCacheRequest(content);
            String responseBody = callCreateCacheApi(requestBody);

            ContextCacheConfig config = parseCreateCacheResponse(responseBody);

            // 关联 sessionId 和 cacheId
            if (config.getCacheId() != null) {
                sessionCacheMap.put(sessionId, config.getCacheId());
            }

            log.info("[ContextCache] 缓存创建成功 - sessionId={}, cacheId={}",
                    sessionId, config.getCacheId());

            return config;

        } catch (Exception e) {
            log.error("[ContextCache] 创建缓存失败 - sessionId={}: {}", sessionId, e.getMessage(), e);
            return ContextCacheConfig.builder()
                    .status("error")
                    .build();
        }
    }

    /**
     * 获取会话关联的缓存 ID。
     *
     * @param sessionId 会话 ID
     * @return 缓存 ID，如果没有则返回 null
     */
    public String getCacheId(String sessionId) {
        return sessionCacheMap.get(sessionId);
    }

    /**
     * 使用缓存的会话进行对话。
     *
     * @param request LLM 请求
     * @param cacheId 缓存 ID
     * @return LLM 响应
     */
    public LlmResponse chatWithCache(LlmRequest request, String cacheId) {
        log.info("[ContextCache] 使用缓存对话 - sessionId={}, cacheId={}",
                request.getSessionId(), cacheId);

        try {
            String requestBody = buildChatWithCacheRequest(request, cacheId);
            String responseBody = callChatApi(requestBody);

            return parseChatResponse(responseBody, request.getModel());

        } catch (Exception e) {
            log.error("[ContextCache] 缓存对话失败 - sessionId={}: {}", request.getSessionId(), e.getMessage(), e);
            return LlmResponse.error("CONTEXT_CACHE_ERROR", "缓存对话失败: " + e.getMessage());
        }
    }

    /**
     * 使用会话关联的缓存进行对话。
     * 如果没有缓存，自动创建新缓存。
     *
     * @param request LLM 请求
     * @param sessionId 会话 ID
     * @return LLM 响应
     */
    public LlmResponse chatWithSessionCache(LlmRequest request, String sessionId) {
        String promptFingerprint = fingerprint(request.getSystemPrompt());
        String cacheId = getCacheId(sessionId);
        String cachedFingerprint = sessionPromptFingerprintMap.get(sessionId);

        if (cacheId == null || !Objects.equals(promptFingerprint, cachedFingerprint)) {
            if (cacheId != null && !Objects.equals(promptFingerprint, cachedFingerprint)) {
                log.info("[ContextCache] 检测到 prompt 已变化，刷新缓存 - sessionId={}", sessionId);
                deleteCache(cacheId);
            }

            if (request.getSystemPrompt() != null) {
                // 没有缓存或 prompt 已变化，创建新缓存
                ContextCacheConfig config = createCache(request.getSystemPrompt(), sessionId);
                cacheId = config.getCacheId();
                if (cacheId != null) {
                    sessionPromptFingerprintMap.put(sessionId, promptFingerprint);
                }
            }
        }

        if (cacheId == null) {
            // 仍然没有缓存，降级到普通对话
            log.warn("[ContextCache] 无可用缓存，降级到普通对话 - sessionId={}", sessionId);
            return null;
        }

        return chatWithCache(request, cacheId);
    }

    /**
     * 删除缓存。
     *
     * @param cacheId 缓存 ID
     * @return 是否删除成功
     */
    public boolean deleteCache(String cacheId) {
        log.info("[ContextCache] 删除缓存 - cacheId={}", cacheId);

        try {
            String url = baseUrl + "/api/v1/contexts/caches/" + cacheId;
            java.util.List<String> relatedSessionIds = sessionCacheMap.entrySet().stream()
                    .filter(entry -> cacheId.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .DELETE()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 从 sessionCacheMap 中移除关联
            sessionCacheMap.entrySet().removeIf(entry -> entry.getValue().equals(cacheId));
            relatedSessionIds.forEach(sessionPromptFingerprintMap::remove);

            return response.statusCode() >= 200 && response.statusCode() < 300;

        } catch (Exception e) {
            log.error("[ContextCache] 删除缓存失败 - cacheId={}: {}", cacheId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取缓存信息。
     *
     * @param cacheId 缓存 ID
     * @return 缓存配置
     */
    public ContextCacheConfig getCacheInfo(String cacheId) {
        log.info("[ContextCache] 获取缓存信息 - cacheId={}", cacheId);

        try {
            String url = baseUrl + "/api/v1/contexts/caches/" + cacheId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseCacheInfoResponse(response.body());
            } else {
                log.warn("[ContextCache] 获取缓存信息失败 - cacheId={}, status={}",
                        cacheId, response.statusCode());
                return ContextCacheConfig.builder()
                        .cacheId(cacheId)
                        .status("not_found")
                        .build();
            }

        } catch (Exception e) {
            log.error("[ContextCache] 获取缓存信息失败 - cacheId={}: {}", cacheId, e.getMessage(), e);
            return ContextCacheConfig.builder()
                    .cacheId(cacheId)
                    .status("error")
                    .build();
        }
    }

    // ==================== Private Methods ====================

    private String buildCreateCacheRequest(String content) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "qwen-plus");
        body.put("content", content);
        return objectMapper.writeValueAsString(body);
    }

    private String callCreateCacheApi(String requestBody) throws Exception {
        String url = baseUrl + "/api/v1/contexts/caches";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("创建缓存失败: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private ContextCacheConfig parseCreateCacheResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        ContextCacheConfig.ContextCacheConfigBuilder builder = ContextCacheConfig.builder();

        if (root.has("output")) {
            JsonNode output = root.get("output");
            builder.cacheId(output.has("cache_id") ? output.get("cache_id").asText() : null);
            builder.contentLength(output.has("content_length") ? output.get("content_length").asInt() : null);
            builder.createdAt(output.has("created_at") ? output.get("created_at").asLong() : null);
            builder.expiresAt(output.has("expires_at") ? output.get("expires_at").asLong() : null);
            builder.model(output.has("model") ? output.get("model").asText() : null);
        }

        builder.status("active");
        return builder.build();
    }

    private String buildChatWithCacheRequest(LlmRequest request, String cacheId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : "qwen-plus");
        body.put("cache_id", cacheId);

        Map<String, Object> input = new HashMap<>();
        StringBuilder prompt = new StringBuilder();

        if (request.getMessages() != null) {
            for (LlmRequest.ChatMessage msg : request.getMessages()) {
                if (msg != null && msg.getContent() != null) {
                    prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
                }
            }
        }
        input.put("prompt", prompt.toString().trim());

        body.put("input", input);

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

    private String callChatApi(String requestBody) throws Exception {
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
            throw new RuntimeException("缓存对话失败: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private LlmResponse parseChatResponse(String responseBody, String model) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        if (root.has("error")) {
            JsonNode error = root.get("error");
            String code = error.has("code") ? error.get("code").asText() : "UNKNOWN";
            String message = error.has("message") ? error.get("message").asText() : "Unknown error";
            return LlmResponse.error(code, message);
        }

        String content = "";
        String finishReason = null;

        JsonNode output = root.get("output");
        if (output != null) {
            JsonNode choices = output.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.get("message");
                if (message != null) {
                    content = message.has("content") ? message.get("content").asText() : "";
                }
                finishReason = firstChoice.has("finish_reason") ? firstChoice.get("finish_reason").asText() : null;
            }
        }

        return LlmResponse.builder()
                .success(true)
                .content(content)
                .model(model != null ? model : "qwen-plus")
                .provider(com.liang.drugagent.shared.llm.LlmProviderType.DASHSCOPE)
                .finishReason(finishReason)
                .build();
    }

    private ContextCacheConfig parseCacheInfoResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        ContextCacheConfig.ContextCacheConfigBuilder builder = ContextCacheConfig.builder();

        if (root.has("output")) {
            JsonNode output = root.get("output");
            builder.cacheId(output.has("cache_id") ? output.get("cache_id").asText() : null);
            builder.contentLength(output.has("content_length") ? output.get("content_length").asInt() : null);
            builder.createdAt(output.has("created_at") ? output.get("created_at").asLong() : null);
            builder.expiresAt(output.has("expires_at") ? output.get("expires_at").asLong() : null);
            builder.model(output.has("model") ? output.get("model").asText() : null);

            if (output.has("status")) {
                builder.status(output.get("status").asText());
            }
        }

        return builder.build();
    }

    private String fingerprint(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.warn("[ContextCache] 生成 prompt 指纹失败，降级使用原文长度标识: {}", e.getMessage());
            return "len:" + content.length();
        }
    }
}
