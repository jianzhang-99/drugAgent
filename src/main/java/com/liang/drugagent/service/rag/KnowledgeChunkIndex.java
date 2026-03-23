package com.liang.drugagent.service.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.io.File;
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
 * MVP 阶段关键词索引（内存 + 本地持久化）。
 *
 * 当前实现：简化 BM25（支持 IDF、TF、长度归一）。
 * 为避免重启后候选丢失，索引会持久化到本地文件。
 */
@Component
public class KnowledgeChunkIndex {

    private static final double K1 = 1.5D;
    private static final double B = 0.75D;
    private static final String INDEX_FILE = "knowledge_chunk_index.json";

    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeChunkIndex() {
        loadIfExists();
    }

    public void addAll(List<Entry> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        entries.addAll(batch);
        persist();
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

        double avgDocLen = Math.max((double) totalLength / candidates.size(), 1D);
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

    private void loadIfExists() {
        try {
            File file = new File(INDEX_FILE);
            if (!file.exists()) {
                return;
            }
            List<Entry> loaded = objectMapper.readValue(file, new TypeReference<List<Entry>>() {
            });
            entries.clear();
            if (loaded != null) {
                entries.addAll(loaded);
            }
        } catch (Exception ignored) {
            // 索引文件损坏时忽略加载，避免影响应用启动。
        }
    }

    private void persist() {
        try {
            objectMapper.writeValue(new File(INDEX_FILE), entries);
        } catch (Exception ignored) {
            // 持久化失败不影响主流程，但会丢失重启恢复能力。
        }
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
