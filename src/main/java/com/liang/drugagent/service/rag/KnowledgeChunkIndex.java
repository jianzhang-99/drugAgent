package com.liang.drugagent.service.rag;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MVP 阶段关键词索引（内存）。
 *
 * 当前实现：简化 BM25（支持 IDF、TF 归一、长度归一），
 * 便于在不引入外部搜索引擎的情况下先落地混合检索。
 */
@Component
public class KnowledgeChunkIndex {

    private static final double K1 = 1.5D;
    private static final double B = 0.75D;

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public void addAll(List<Entry> batch) {
        entries.addAll(batch);
    }

    public List<Entry> all() {
        return new ArrayList<>(entries);
    }

    public Map<String, Double> bm25Search(List<Entry> candidates, List<String> terms, int topN) {
        if (candidates == null || candidates.isEmpty() || terms == null || terms.isEmpty()) {
            return Map.of();
        }

        Set<String> normalizedTerms = new HashSet<>();
        for (String term : terms) {
            if (term != null && !term.isBlank()) {
                normalizedTerms.add(term);
            }
        }
        if (normalizedTerms.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> docLength = new HashMap<>();
        Map<String, Map<String, Integer>> tfMap = new HashMap<>();
        Map<String, Integer> dfMap = new HashMap<>();

        int totalLength = 0;
        for (Entry entry : candidates) {
            List<String> tokens = tokenize(entry.getContent());
            int len = Math.max(tokens.size(), 1);
            totalLength += len;
            docLength.put(entry.getChunkId(), len);

            Map<String, Integer> tf = new HashMap<>();
            Set<String> appeared = new HashSet<>();
            for (String token : tokens) {
                if (!normalizedTerms.contains(token)) {
                    continue;
                }
                tf.put(token, tf.getOrDefault(token, 0) + 1);
                appeared.add(token);
            }
            tfMap.put(entry.getChunkId(), tf);
            for (String term : appeared) {
                dfMap.put(term, dfMap.getOrDefault(term, 0) + 1);
            }
        }

        double avgDocLen = (double) totalLength / candidates.size();
        int totalDocs = candidates.size();
        Map<String, Double> scores = new HashMap<>();

        for (Entry entry : candidates) {
            String chunkId = entry.getChunkId();
            Map<String, Integer> tf = tfMap.getOrDefault(chunkId, Map.of());
            int dl = docLength.getOrDefault(chunkId, 1);
            double score = 0D;

            for (String term : normalizedTerms) {
                int f = tf.getOrDefault(term, 0);
                if (f <= 0) {
                    continue;
                }
                int df = dfMap.getOrDefault(term, 0);
                double idf = Math.log(1 + (totalDocs - df + 0.5D) / (df + 0.5D));
                double denominator = f + K1 * (1 - B + B * dl / avgDocLen);
                score += idf * ((f * (K1 + 1)) / denominator);
            }

            if (score > 0D) {
                scores.put(chunkId, score);
            }
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(topN)
                .collect(HashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        HashMap::putAll);
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", " ");
        String[] parts = normalized.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }

    @Getter
    @Setter
    public static class Entry {
        private String chunkId;
        private String content;
        private Map<String, Object> metadata;
        private float[] vector;
        private Instant createdAt = Instant.now();
    }
}
