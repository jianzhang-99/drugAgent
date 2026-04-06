package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.shared.rag.model.RagChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务。
 *
 * <p>结合 BM25 关键词检索和向量相似度检索，提高召回质量。
 * 当前版本使用内存中的简单 BM25 实现，适合中小规模知识库。</p>
 */
@Slf4j
@Service
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;

    // BM25 参数
    private static final double BM25_K1 = 1.5;
    private static final double BM25_B = 0.75;

    public HybridSearchService(VectorStore vectorStore, EmbeddingService embeddingService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
    }

    /**
     * 执行混合检索（BM25 + 向量）
     *
     * @param query 查询文本
     * @param documents 待检索的文档列表
     * @param topK 返回数量
     * @param bm25Weight BM25权重 (0-1)，向量权重为 1 - bm25Weight
     * @return 混合检索结果列表
     */
    public List<RagChunk> hybridSearch(String query, List<Document> documents, int topK, double bm25Weight) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 计算 BM25 分数
        Map<String, Double> bm25Scores = calculateBM25(query, documents);

        // 2. 计算向量相似度分数
        Map<String, Double> vectorScores = calculateVectorScores(query, documents);

        // 3. 合并分数
        Map<String, Double> combinedScores = new HashMap<>();
        for (Document doc : documents) {
            String docId = doc.getId();
            double bm25 = bm25Scores.getOrDefault(docId, 0.0);
            double vector = vectorScores.getOrDefault(docId, 0.0);
            double combined = bm25Weight * bm25 + (1 - bm25Weight) * vector;
            combinedScores.put(docId, combined);
        }

        // 4. 排序并返回 topK
        List<String> sortedDocIds = combinedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(topK)
                .collect(Collectors.toList());

        // 5. 构建结果
        Map<String, Document> docMap = documents.stream()
                .collect(Collectors.toMap(Document::getId, d -> d));

        List<RagChunk> results = new ArrayList<>();
        for (String docId : sortedDocIds) {
            Document doc = docMap.get(docId);
            if (doc != null) {
                results.add(toRagChunk(doc, combinedScores.get(docId)));
            }
        }

        log.info("混合检索完成 - 查询={}, 原始文档数={}, 返回数={}", query, documents.size(), results.size());
        return results;
    }

    /**
     * 计算 BM25 分数
     */
    private Map<String, Double> calculateBM25(String query, List<Document> documents) {
        // 分词（简单实现，实际应使用专业分词器）
        List<String> queryTerms = tokenize(query);

        // 计算平均文档长度
        double avgDocLen = documents.stream()
                .mapToLong(doc -> tokenize(doc.getText()).size())
                .average()
                .orElse(100.0);

        // 统计词频
        Map<String, Integer> docFreq = new HashMap<>();
        for (Document doc : documents) {
            Set<String> uniqueTerms = new HashSet<>(tokenize(doc.getText()));
            for (String term : uniqueTerms) {
                docFreq.merge(term, 1, Integer::sum);
            }
        }

        // 计算每个文档的 BM25 分数
        Map<String, Double> scores = new HashMap<>();
        int N = documents.size();

        for (Document doc : documents) {
            List<String> docTerms = tokenize(doc.getText());
            double score = 0.0;
            double docLen = docTerms.size();

            for (String term : queryTerms) {
                int tf = (int) docTerms.stream().filter(t -> t.equals(term)).count();
                if (tf > 0) {
                    int df = docFreq.getOrDefault(term, 0);
                    double idf = Math.log((N - df + 0.5) / (df + 0.5) + 1);
                    double termScore = idf * (tf * (BM25_K1 + 1)) / (tf + BM25_K1 * (1 - BM25_B + BM25_B * docLen / avgDocLen));
                    score += termScore;
                }
            }
            scores.put(doc.getId(), score);
        }

        // 归一化
        normalizeScores(scores);
        return scores;
    }

    /**
     * 计算向量相似度分数
     */
    private Map<String, Double> calculateVectorScores(String query, List<Document> documents) {
        if (documents.isEmpty()) {
            return new HashMap<>();
        }

        float[] queryEmbedding = embeddingService.embed(query);
        Map<String, Double> scores = new HashMap<>();

        for (Document doc : documents) {
            // 文档向量需要提前存储或计算，这里简化处理
            // 实际实现应该从向量存储中获取
            scores.put(doc.getId(), doc.getScore() != null ? doc.getScore().doubleValue() : 0.0);
        }

        // 归一化
        normalizeScores(scores);
        return scores;
    }

    /**
     * 归一化分数到 0-1
     */
    private void normalizeScores(Map<String, Double> scores) {
        if (scores.isEmpty()) {
            return;
        }
        double maxScore = scores.values().stream().max(Double::compare).orElse(1.0);
        double minScore = scores.values().stream().min(Double::compare).orElse(0.0);
        double range = maxScore - minScore;
        if (range > 0) {
            scores.replaceAll((k, v) -> (v - minScore) / range);
        } else {
            scores.replaceAll((k, v) -> 1.0);
        }
    }

    /**
     * 简单分词
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(text.toLowerCase().split("[\\s\\p{Punct}]+"))
                .filter(t -> t.length() > 1)
                .collect(Collectors.toList());
    }

    /**
     * 将 Document 转换为 RagChunk
     */
    private RagChunk toRagChunk(Document doc, Double score) {
        Map<String, Object> metadata = doc.getMetadata();
        com.liang.drugagent.shared.rag.model.ChunkMetadata chunkMetadata =
                com.liang.drugagent.shared.rag.model.ChunkMetadata.builder()
                        .orgId(getStringValue(metadata, "orgId"))
                        .scene(getStringValue(metadata, "scene"))
                        .subScene(getStringValue(metadata, "subScene"))
                        .docType(getStringValue(metadata, "docType"))
                        .sourceId(getStringValue(metadata, "sourceId"))
                        .sourceTitle(getStringValue(metadata, "sourceTitle"))
                        .chunkId(doc.getId())
                        .chunkIndex(getIntValue(metadata, "chunkIndex"))
                        .sectionTitle(getStringValue(metadata, "sectionTitle"))
                        .pageNo(getIntValue(metadata, "pageNo"))
                        .version(getStringValue(metadata, "version"))
                        .build();

        return com.liang.drugagent.shared.rag.model.RagChunk.builder()
                .chunkId(doc.getId())
                .content(doc.getText())
                .metadata(chunkMetadata)
                .score(score != null ? score.floatValue() : null)
                .build();
    }

    private String getStringValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getIntValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
