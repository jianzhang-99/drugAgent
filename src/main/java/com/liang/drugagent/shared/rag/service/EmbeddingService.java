package com.liang.drugagent.shared.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量嵌入服务。
 *
 * <p>封装文本到向量的转换逻辑。</p>
 */
@Slf4j
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 单条文本 embedding
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("嵌入文本不能为空");
        }

        EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
        EmbeddingResponse response = embeddingModel.call(request);

        List<Embedding> results = response.getResults();
        if (results.isEmpty()) {
            throw new RuntimeException("Embedding 返回结果为空");
        }

        return results.get(0).getOutput();
    }

    /**
     * 批量文本 embedding
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        EmbeddingRequest request = new EmbeddingRequest(texts, null);
        EmbeddingResponse response = embeddingModel.call(request);

        return response.getResults().stream()
                .map(Embedding::getOutput)
                .toList();
    }

    /**
     * 获取向量维度
     */
    public int getDimensions() {
        // 先调用一次 embedding 获取向量维度
        float[] sample = embed("dimension check");
        return sample.length;
    }
}
