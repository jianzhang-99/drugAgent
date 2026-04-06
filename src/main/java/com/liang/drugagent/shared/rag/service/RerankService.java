package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.shared.rag.model.RagChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 重排服务。
 *
 * <p>对初步检索结果进行二次排序，提高相关性。
 * 当前版本基于文档与查询的语义相似度和多样性进行重排。</p>
 */
@Slf4j
@Service
public class RerankService {

    /**
     * 对检索结果进行重排。
     *
     * @param query 查询文本
     * @param chunks 待重排的 chunks
     * @param topK 返回数量
     * @return 重排后的结果
     */
    public List<RagChunk> rerank(String query, List<RagChunk> chunks, int topK) {
        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<>();
        }

        if (chunks.size() <= topK) {
            return new ArrayList<>(chunks);
        }

        // 1. 计算每个 chunk 与查询的相关性分数
        Map<String, Double> relevanceScores = calculateRelevance(query, chunks);

        // 2. 计算多样性分数（避免重复来源）
        Map<String, Double> diversityScores = calculateDiversity(chunks);

        // 3. 综合评分 = 0.7 * 相关性 + 0.3 * 多样性
        Map<String, Double> combinedScores = new HashMap<>();
        for (RagChunk chunk : chunks) {
            double relevance = relevanceScores.getOrDefault(chunk.getChunkId(), 0.0);
            double diversity = diversityScores.getOrDefault(chunk.getChunkId(), 0.0);
            combinedScores.put(chunk.getChunkId(), 0.7 * relevance + 0.3 * diversity);
        }

        // 4. 排序并返回 topK
        List<String> sortedChunkIds = combinedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(topK)
                .collect(Collectors.toList());

        // 5. 构建结果
        Map<String, RagChunk> chunkMap = chunks.stream()
                .collect(Collectors.toMap(RagChunk::getChunkId, c -> c));

        List<RagChunk> results = new ArrayList<>();
        for (String chunkId : sortedChunkIds) {
            RagChunk chunk = chunkMap.get(chunkId);
            if (chunk != null) {
                results.add(chunk);
            }
        }

        log.info("重排完成 - 查询={}, 原始数量={}, 返回数={}", query, chunks.size(), results.size());
        return results;
    }

    /**
     * 计算相关性分数
     */
    private Map<String, Double> calculateRelevance(String query, List<RagChunk> chunks) {
        List<String> queryTerms = tokenize(query.toLowerCase());
        Map<String, Double> scores = new HashMap<>();

        for (RagChunk chunk : chunks) {
            String content = chunk.getContent() != null ? chunk.getContent().toLowerCase() : "";
            List<String> contentTerms = tokenize(content);

            if (contentTerms.isEmpty()) {
                scores.put(chunk.getChunkId(), 0.0);
                continue;
            }

            // 计算查询词在内容中的覆盖率
            long matchCount = queryTerms.stream()
                    .filter(contentTerms::contains)
                    .count();
            double coverage = (double) matchCount / queryTerms.size();

            // 计算查询词在内容中的密度（出现次数）
            long termCount = queryTerms.stream()
                    .filter(contentTerms::contains)
                    .mapToLong(term -> contentTerms.stream().filter(t -> t.equals(term)).count())
                    .sum();
            double density = Math.min(1.0, (double) termCount / contentTerms.size());

            // 综合相关性分数
            double score = 0.6 * coverage + 0.4 * density;
            scores.put(chunk.getChunkId(), score);
        }

        // 归一化
        normalizeScores(scores);
        return scores;
    }

    /**
     * 计算多样性分数
     *
     * <p>同一来源（sourceId）的文档只保留一个，减少重复。</p>
     */
    private Map<String, Double> calculateDiversity(List<RagChunk> chunks) {
        // 统计每个 sourceId 出现的次数
        Map<String, Long> sourceIdCounts = new HashMap<>();
        for (RagChunk chunk : chunks) {
            String sourceId = chunk.getMetadata() != null ? chunk.getMetadata().getSourceId() : "";
            sourceIdCounts.merge(sourceId, 1L, Long::sum);
        }

        // 计算多样性分数：出现次数越少，分数越高
        Map<String, Double> scores = new HashMap<>();
        for (RagChunk chunk : chunks) {
            String sourceId = chunk.getMetadata() != null ? chunk.getMetadata().getSourceId() : "";
            long count = sourceIdCounts.getOrDefault(sourceId, 1L);
            scores.put(chunk.getChunkId(), 1.0 / count);
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
        return Arrays.stream(text.split("[\\s\\p{Punct}]+"))
                .filter(t -> t.length() > 1)
                .collect(Collectors.toList());
    }
}
