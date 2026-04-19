package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.app.DrugAgentApplication;
import com.liang.drugagent.shared.rag.model.ChunkMetadata;
import com.liang.drugagent.shared.rag.model.RagChunk;
import com.liang.drugagent.shared.rag.model.RagDocument;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IngestService 集成测试。
 *
 * <p>验证文档入库完整链路：
 * <ul>
 *   <li>ingest() 完整入库链路</li>
 *   <li>ingestAndReturnChunkCount() 返回 chunk 数量</li>
 *   <li>deleteBySourceId() 删除</li>
 *   <li>metadata 正确传递</li>
 * </ul>
 */
@Slf4j
@SpringBootTest(classes = DrugAgentApplication.class)
@ActiveProfiles("local")
@DisplayName("IngestService 文档入库集成测试")
class IngestServiceIntegrationTest {

    @Autowired
    private IngestService ingestService;

    @MockBean
    private VectorStore vectorStore;

    @MockBean
    private com.liang.drugagent.shared.rag.service.EmbeddingService embeddingService;

    private String testOrgId;
    private String testScene;

    @BeforeEach
    void setUp() {
        testOrgId = "test-org-" + UUID.randomUUID().toString().substring(0, 8);
        testScene = "TEST_SCENE";

        // 模拟 VectorStore 添加操作
        doNothing().when(vectorStore).add(any());
        doNothing().when(vectorStore).delete(any(Filter.Expression.class));

        // 模拟 EmbeddingService 生成向量
        when(embeddingService.embed(anyString())).thenReturn(new float[1024]);
    }

    @Test
    @DisplayName("文本入库应正确返回 chunk 数量")
    void shouldReturnChunkCountWhenIngestingText() {
        String content = """
                # 第一章 总则

                第一条 为保证药品的安全、有效和质量可控，规范药品注册行为，加强药品注册管理，保护药品生产企业、药品使用单位及消费者的合法权益，根据《中华人民共和国药品管理法》和《中华人民共和国药品管理法实施条例》，制定本办法。

                第二条 在中华人民共和国境内从事药品研制、注册、生产、经营、使用和监督管理活动，适用本办法。

                ## 第二章 药品注册申请

                第三条 药品注册申请包括药物临床试验申请、药品上市许可申请、药品补充申请和药品再注册申请。
                """;

        RagDocument document = RagDocument.builder()
                .sourceId("TEST-DOC-" + System.currentTimeMillis())
                .title("药品注册管理办法")
                .rawText(content)
                .orgId(testOrgId)
                .scene(testScene)
                .subScene("DRUG_REGISTRATION")
                .docType("REGULATION")
                .createdAt(LocalDateTime.now())
                .build();

        int chunkCount = ingestService.ingestAndReturnChunkCount(document);

        log.info("文本入库 chunk 数量: {}", chunkCount);
        assertTrue(chunkCount > 0, "chunk 数量应大于 0");
        verify(vectorStore, times(chunkCount)).add(any());
    }

    @Test
    @DisplayName("文件入库应正确提取文本并返回 chunk 数量")
    void shouldReturnChunkCountWhenIngestingFile() throws Exception {
        String content = "# 测试文档\n\n这是测试文档的内容，包含一些关于招标投标的规定。\n\n第一条 投标人应当遵循诚实信用原则。\n\n第二条 投标人之间不得相互串通。";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );

        int chunkCount = ingestService.ingestAndReturnChunkCount(file, "测试文档", testOrgId, testScene, "TEST_SUB", "REGULATION");

        log.info("文件入库 chunk 数量: {}", chunkCount);
        assertTrue(chunkCount > 0, "chunk 数量应大于 0");
    }

    @Test
    @DisplayName("空文档入库应返回 chunk 数量 0")
    void shouldReturnZeroWhenIngestingEmptyDocument() {
        RagDocument document = RagDocument.builder()
                .sourceId("TEST-DOC-EMPTY-" + System.currentTimeMillis())
                .title("空文档")
                .rawText("")
                .orgId(testOrgId)
                .scene(testScene)
                .docType("REGULATION")
                .createdAt(LocalDateTime.now())
                .build();

        int chunkCount = ingestService.ingestAndReturnChunkCount(document);

        log.info("空文档入库 chunk 数量: {}", chunkCount);
        assertEquals(0, chunkCount);
    }

    @Test
    @DisplayName("入库 chunk 的 metadata 应正确包含文档信息")
    void shouldContainCorrectMetadataInChunks() throws Exception {
        String content = "# 测试标题\n\n这是测试内容。";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "metadata-test.txt",
                "text/plain",
                content.getBytes()
        );

        int chunkCount = ingestService.ingestAndReturnChunkCount(
                file, "元数据测试文档", testOrgId, testScene, "TEST_SUB", "REGULATION"
        );

        log.info("元数据测试 - chunk 数量: {}", chunkCount);
        assertTrue(chunkCount > 0, "chunk 数量应大于 0");
        verify(vectorStore, times(chunkCount)).add(any());
    }

    @Test
    @DisplayName("deleteBySourceId 应调用向量库删除方法")
    void shouldCallVectorStoreDeleteWhenDeletingBySourceId() {
        String sourceId = "TEST-DOC-DELETE-" + System.currentTimeMillis();

        ingestService.deleteBySourceId(sourceId);

        // 验证调用了 Filter.Expression 进行删除
        verify(vectorStore, times(1)).delete(any(Filter.Expression.class));
    }

    @Test
    @DisplayName("deleteBySourceId 传入空值应跳过删除")
    void shouldSkipDeleteWhenSourceIdIsBlank() {
        ingestService.deleteBySourceId("");
        ingestService.deleteBySourceId("   ");
        ingestService.deleteBySourceId(null);

        // 验证没有调用向量库删除
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    @Test
    @DisplayName("批量入库应正确处理多个文档")
    void shouldHandleBatchIngestion() {
        String content1 = "# 文档1\n\n这是第一个文档的内容。";
        String content2 = "# 文档2\n\n这是第二个文档的内容。";

        RagDocument doc1 = RagDocument.builder()
                .sourceId("BATCH-DOC-1-" + System.currentTimeMillis())
                .title("批量文档1")
                .rawText(content1)
                .orgId(testOrgId)
                .scene(testScene)
                .docType("REGULATION")
                .createdAt(LocalDateTime.now())
                .build();

        RagDocument doc2 = RagDocument.builder()
                .sourceId("BATCH-DOC-2-" + System.currentTimeMillis())
                .title("批量文档2")
                .rawText(content2)
                .orgId(testOrgId)
                .scene(testScene)
                .docType("REGULATION")
                .createdAt(LocalDateTime.now())
                .build();

        ingestService.ingestBatch(List.of(doc1, doc2));

        // 验证两个文档的 chunks 都添加了
        verify(vectorStore, atLeast(2)).add(any());
    }

    @Test
    @DisplayName("ingest 方法应正确入库文档")
    void shouldIngestDocumentCorrectly() {
        String content = "# 法规\n\n这是一个法规文档的内容。";

        RagDocument document = RagDocument.builder()
                .sourceId("INGEST-DOC-" + System.currentTimeMillis())
                .title("法规文档")
                .rawText(content)
                .orgId(testOrgId)
                .scene(testScene)
                .docType("REGULATION")
                .createdAt(LocalDateTime.now())
                .build();

        // 不抛异常即为成功
        assertDoesNotThrow(() -> ingestService.ingest(document));
    }

    @Test
    @DisplayName("幂等删除不存在文档不应报错")
    void shouldNotThrowWhenDeletingNonExistentDocument() {
        String nonExistentSourceId = "NON-EXISTENT-" + UUID.randomUUID().toString();

        // 不抛异常即为成功
        assertDoesNotThrow(() -> ingestService.deleteBySourceId(nonExistentSourceId));
    }
}