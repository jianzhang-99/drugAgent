package com.liang.drugagent.shared.rag.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 直连 DashScope 的 Embedding 模型。
 *
 * <p>Spring AI 默认 RestClient 使用系统代理，但 macOS 的代理（如 Charles/Fiddler/V2Ray）
 * 会拦截并篡改 HTTPS 请求，导致 DashScope 返回 404。
 * 此实现通过设置 Proxy.NO_PROXY 强制所有请求直连，绕过系统代理。</p>
 */
@Component
@Primary
public class DirectDashScopeEmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;
    private final String model;

    public DirectDashScopeEmbeddingModel(
            @Value("${aliyun.dashscope.embedding-model:text-embedding-v3}") String model,
            @Value("${aliyun.dashscope.api-key}") String apiKey,
            @Value("${aliyun.dashscope.embedding-base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl) {

        this.model = model;
        // 兼容模式使用 OpenAI 兼容接口，原生接口路径不同
        String resolvedBaseUrl = (baseUrl != null && !baseUrl.isBlank())
                ? baseUrl
                : "https://dashscope.aliyuncs.com/compatible-mode/v1";

        // 强制直连，绕过系统代理（Proxy.NO_PROXY 表示完全不经过任何代理）
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);

        this.restClient = RestClient.builder()
                .baseUrl(resolvedBaseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();

        Map<String, Object> body = Map.of(
                "input", inputs,
                "model", model
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> raw = restClient.post()
                .uri("/embeddings")
                .body(body)
                .retrieve()
                .body(Map.class);

        if (raw == null) {
            throw new RuntimeException("Embedding API 返回为空");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) raw.get("data");

        List<Embedding> embeddings = new ArrayList<>();
        for (Map<String, Object> item : dataList) {
            @SuppressWarnings("unchecked")
            List<Number> emb = (List<Number>) item.get("embedding");
            float[] vec = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                vec[i] = emb.get(i).floatValue();
            }
            Integer index = item.get("index") != null ? ((Number) item.get("index")).intValue() : 0;
            embeddings.add(new Embedding(vec, index));
        }

        EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
        Object modelName = raw.get("model");
        if (modelName != null) {
            metadata.setModel(modelName.toString());
        }

        return new EmbeddingResponse(embeddings, metadata);
    }
}
