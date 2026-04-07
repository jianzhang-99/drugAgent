package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.shared.rag.model.ChunkMetadata;
import com.liang.drugagent.shared.rag.model.RagChunk;
import com.liang.drugagent.shared.rag.model.RagDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档入库服务。
 *
 * <p>负责文档文本提取、chunk 切分、embedding 生成和向量库存储。
 * 使用 PGVector 原生 Filter API 按 sourceId 删除。</p>
 */
@Slf4j
@Service
public class IngestService {

    private final TextExtractor textExtractor;
    private final Chunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public IngestService(TextExtractor textExtractor, Chunker chunker,
                        EmbeddingService embeddingService, VectorStore vectorStore) {
        this.textExtractor = textExtractor;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 从 MultipartFile 入库文档
     */
    public void ingest(MultipartFile file, String title, String orgId, String scene,
                       String subScene, String docType) throws IOException {
        String sourceId = generateSourceId();
        String text = textExtractor.extract(file);

        RagDocument document = RagDocument.builder()
                .sourceId(sourceId)
                .title(title)
                .rawText(text)
                .orgId(orgId)
                .scene(scene)
                .subScene(subScene)
                .docType(docType)
                .createdAt(LocalDateTime.now())
                .build();

        ingest(document);
    }

    /**
     * 入库 RagDocument
     */
    public void ingest(RagDocument document) {
        // 1. 切分 chunk
        List<RagChunk> chunks = chunker.chunk(document);
        if (chunks.isEmpty()) {
            log.warn("文档切分后无有效 chunk，跳导入库: sourceId={}", document.getSourceId());
            return;
        }

        // 2. 生成 embedding 并入库
        for (RagChunk chunk : chunks) {
            float[] embedding = embeddingService.embed(chunk.getContent());
            chunk.setEmbedding(embedding);

            // 3. 转换为 Spring AI Document 并存储
            Document aiDoc = toAiDocument(chunk);
            vectorStore.add(List.of(aiDoc));
        }

        // 持久化
        save();

        log.info("文档入库完成 - sourceId={}, title={}, chunk数量={}",
                document.getSourceId(), document.getTitle(), chunks.size());
    }

    /**
     * 批量入库
     */
    public void ingestBatch(List<RagDocument> documents) {
        for (RagDocument doc : documents) {
            ingest(doc);
        }
    }

    /**
     * 保存向量库（PGVector 无需手动保存，自动持久化）
     */
    public void save() {
        log.debug("PGVector 自动持久化，无需手动保存");
    }

    /**
     * 根据 sourceId 删除向量库中该文档的所有 chunks。
     *
     * <p>使用 PGVector 原生 Filter API 按 sourceId 过滤并删除。</p>
     */
    public void deleteBySourceId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            log.warn("deleteBySourceId 跳过：sourceId 为空");
            return;
        }

        try {
            // 使用 PGVector 原生的 Filter API 按 sourceId 删除
            Filter.Expression filter = new Filter.Expression(
                    Filter.ExpressionType.EQ,
                    new Filter.Key("sourceId"),
                    new Filter.Value(sourceId)
            );
            vectorStore.delete(filter);
            log.info("PGVector 删除完成 - sourceId={}", sourceId);
        } catch (Exception e) {
            log.error("PGVector 删除失败 - sourceId={}", sourceId, e);
        }
    }

    /**
     * 将 RagChunk 转换为 Spring AI Document
     */
    private Document toAiDocument(RagChunk chunk) {
        ChunkMetadata metadata = chunk.getMetadata();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("chunkId", metadata.getChunkId() != null ? metadata.getChunkId() : "");
        attributes.put("sourceId", metadata.getSourceId() != null ? metadata.getSourceId() : "");
        attributes.put("sourceTitle", metadata.getSourceTitle() != null ? metadata.getSourceTitle() : "");
        attributes.put("orgId", metadata.getOrgId() != null ? metadata.getOrgId() : "");
        attributes.put("scene", metadata.getScene() != null ? metadata.getScene() : "");
        attributes.put("subScene", metadata.getSubScene() != null ? metadata.getSubScene() : "");
        attributes.put("docType", metadata.getDocType() != null ? metadata.getDocType() : "");
        attributes.put("chunkIndex", metadata.getChunkIndex() != null ? metadata.getChunkIndex() : 0);
        attributes.put("sectionTitle", metadata.getSectionTitle() != null ? metadata.getSectionTitle() : "");
        attributes.put("topicTags", metadata.getTopicTags() != null ? String.join(",", metadata.getTopicTags()) : "");
        attributes.put("pageNo", metadata.getPageNo() != null ? metadata.getPageNo() : 0);
        attributes.put("version", metadata.getVersion() != null ? metadata.getVersion() : "");
        attributes.put("effectiveDate", metadata.getEffectiveDate() != null ? metadata.getEffectiveDate().toString() : "");
        attributes.put("hierarchyLevel", metadata.getHierarchyLevel() != null ? metadata.getHierarchyLevel() : "");
        attributes.put("status", metadata.getStatus() != null ? metadata.getStatus() : "");
        attributes.put("sourceOrg", metadata.getSourceOrg() != null ? metadata.getSourceOrg() : "");

        // PGVector 要求 Document ID 必须是有效的 UUID 格式
        String uuid = UUID.randomUUID().toString();

        return Document.builder()
                .id(uuid)
                .text(chunk.getContent())
                .metadata(attributes)
                .build();
    }

    /**
     * 生成唯一源文档 ID
     */
    private String generateSourceId() {
        return "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
