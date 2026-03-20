package com.liang.drugagent.service.rag;

import com.liang.drugagent.domain.req.KnowledgeIngestTextReq;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 统一知识入库服务。
 */
@Service
public class KnowledgeIngestService {

    private final VectorStore vectorStore;
    private final KnowledgeChunkIndex keywordIndex;
    private final EmbeddingModel embeddingModel;

    public KnowledgeIngestService(VectorStore vectorStore,
                                  KnowledgeChunkIndex keywordIndex,
                                  EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.keywordIndex = keywordIndex;
        this.embeddingModel = embeddingModel;
    }

    public int ingestText(KnowledgeIngestTextReq req) {
        if (req == null || isBlank(req.getContent())) {
            throw new IllegalArgumentException("content 不能为空");
        }
        if (isBlank(req.getOrgId())) {
            throw new IllegalArgumentException("orgId 不能为空");
        }

        String sourceId = isBlank(req.getSourceId()) ? "SRC-" + UUID.randomUUID() : req.getSourceId();
        List<String> chunks = split(req.getContent(), 900, 120);

        List<Document> docs = new ArrayList<>();
        List<KnowledgeChunkIndex.Entry> indexEntries = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = sourceId + "-" + i;
            String text = chunks.get(i);
            Map<String, Object> metadata = buildMetadata(req, sourceId, chunkId, i);
            docs.add(new Document(text, metadata));

            KnowledgeChunkIndex.Entry entry = new KnowledgeChunkIndex.Entry();
            entry.setChunkId(chunkId);
            entry.setContent(text);
            entry.setMetadata(metadata);
            entry.setVector(embed(text));
            indexEntries.add(entry);
        }

        vectorStore.add(docs);
        keywordIndex.addAll(indexEntries);
        return chunks.size();
    }

    private Map<String, Object> buildMetadata(KnowledgeIngestTextReq req, String sourceId, String chunkId, int chunkIndex) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("scene", blankTo(req.getScene(), "general"));
        metadata.put("subScene", blankTo(req.getSubScene(), "general"));
        metadata.put("docType", blankTo(req.getDocType(), "general"));
        metadata.put("orgId", req.getOrgId());
        metadata.put("topicTags", Objects.requireNonNullElseGet(req.getTopicTags(), List::of));
        metadata.put("sourceId", sourceId);
        metadata.put("sourceTitle", blankTo(req.getTitle(), sourceId));
        metadata.put("chunkId", chunkId);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("version", blankTo(req.getVersion(), "v1"));
        metadata.put("createdAt", Instant.now().toString());
        return metadata;
    }

    private List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    private float[] embed(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return new float[0];
        }
        return response.getResults().getFirst().getOutput();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankTo(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
