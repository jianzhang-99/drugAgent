package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.shared.rag.model.ChunkMetadata;
import com.liang.drugagent.shared.rag.model.RagChunk;
import com.liang.drugagent.shared.rag.model.RagDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档入库服务。
 *
 * <p>负责文档文本提取、chunk 切分、embedding 生成和向量库存储。</p>
 */
@Slf4j
@Service
public class IngestService {

    private final TextExtractor textExtractor;
    private final Chunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final File vectorStoreFile;

    public IngestService(TextExtractor textExtractor, Chunker chunker,
                        EmbeddingService embeddingService, VectorStore vectorStore,
                        File vectorStoreFile) {
        this.textExtractor = textExtractor;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.vectorStoreFile = vectorStoreFile;
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

        // 4. 持久化到本地文件
        save();

        log.info("文档入库完成 - sourceId={}, title={}, chunk数量={}",
                document.getSourceId(), document.getTitle(), chunks.size());
    }

    /**
     * 保存向量库到本地文件
     */
    public void save() {
        if (vectorStore instanceof SimpleVectorStore) {
            ((SimpleVectorStore) vectorStore).save(vectorStoreFile);
            log.info("向量库已持久化到: {}", vectorStoreFile.getAbsolutePath());
        }
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
     * 将 RagChunk 转换为 Spring AI Document
     */
    private Document toAiDocument(RagChunk chunk) {
        ChunkMetadata metadata = chunk.getMetadata();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("chunkId", metadata.getChunkId());
        attributes.put("sourceId", metadata.getSourceId());
        attributes.put("sourceTitle", metadata.getSourceTitle() != null ? metadata.getSourceTitle() : "");
        attributes.put("orgId", metadata.getOrgId() != null ? metadata.getOrgId() : "");
        attributes.put("scene", metadata.getScene() != null ? metadata.getScene() : "");
        attributes.put("subScene", metadata.getSubScene() != null ? metadata.getSubScene() : "");
        attributes.put("docType", metadata.getDocType() != null ? metadata.getDocType() : "");
        attributes.put("chunkIndex", metadata.getChunkIndex() != null ? metadata.getChunkIndex() : 0);
        attributes.put("sectionTitle", metadata.getSectionTitle() != null ? metadata.getSectionTitle() : "");
        attributes.put("pageNo", metadata.getPageNo() != null ? metadata.getPageNo() : 0);
        attributes.put("version", metadata.getVersion() != null ? metadata.getVersion() : "");

        return Document.builder()
                .id(chunk.getChunkId())
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
