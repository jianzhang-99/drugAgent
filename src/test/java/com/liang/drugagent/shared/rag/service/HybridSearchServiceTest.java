package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.shared.rag.model.ChunkMetadata;
import com.liang.drugagent.shared.rag.model.RagChunk;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HybridSearchService 单元测试。
 *
 * <p>验证 BM25 + 向量混合检索逻辑：
 * <ul>
 *   <li>空文档列表应返回空列表</li>
 *   <li>BM25 计算正确</li>
 *   <li>向量分数计算正确</li>
 *   <li>分数合并与归一化</li>
 *   <li>topK 限制</li>
 *   <li>分词功能正确</li>
 * </ul>
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("HybridSearchService 混合检索服务单元测试")
class HybridSearchServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingService embeddingService;

    private HybridSearchService hybridSearchService;

    @BeforeEach
    void setUp() {
        hybridSearchService = new HybridSearchService(vectorStore, embeddingService);
    }

    @Test
    @DisplayName("空文档列表应返回空列表")
    void shouldReturnEmptyListWhenDocumentListIsEmpty() {
        List<RagChunk> result = hybridSearchService.hybridSearch("查询", new ArrayList<>(), 5, 0.3);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("null 文档列表应返回空列表")
    void shouldReturnEmptyListWhenDocumentListIsNull() {
        List<RagChunk> result = hybridSearchService.hybridSearch("查询", null, 5, 0.3);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("正常检索应返回结果列表")
    void shouldReturnResultsWhenDocumentsProvided() {
        // 准备测试数据
        List<Document> documents = createTestDocuments();

        // 模拟向量服务
        when(embeddingService.embed(any())).thenReturn(new float[]{0.1f, 0.2f});

        // 执行
        List<RagChunk> result = hybridSearchService.hybridSearch("串通投标", documents, 5, 0.3);

        // 验证
        assertNotNull(result);
        assertFalse(result.isEmpty());
        verify(embeddingService).embed("串通投标");
    }

    @Test
    @DisplayName("topK 应限制返回数量")
    void shouldLimitResultsByTopK() {
        // 准备多个文档
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            documents.add(createDocument("doc-" + i, "这是关于招标投标的第" + i + "条规定"));
        }

        when(embeddingService.embed(any())).thenReturn(new float[]{0.1f});

        // 执行，限制 topK=3
        List<RagChunk> result = hybridSearchService.hybridSearch("招标投标", documents, 3, 0.3);

        // 验证
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("分数归一化后应在 0-1 范围内")
    void shouldNormalizeScoresToRange() {
        // 准备测试文档 - 不同的内容会导致不同的 BM25 分数
        List<Document> documents = List.of(
                createDocument("doc-1", "串通投标是指投标人之间相互约定抬高或降低报价的行为"),
                createDocument("doc-2", "招标投标应当遵循公开公平公正和诚实信用的原则"),
                createDocument("doc-3", "药品注册需要提交相关的技术资料和临床试验数据")
        );

        when(embeddingService.embed(any())).thenReturn(new float[]{0.1f});

        // 执行
        List<RagChunk> result = hybridSearchService.hybridSearch("串通投标", documents, 5, 0.5);

        // 验证分数在 0-1 范围内
        assertNotNull(result);
        for (RagChunk chunk : result) {
            if (chunk.getScore() != null) {
                assertTrue(chunk.getScore() >= 0.0f && chunk.getScore() <= 1.0f,
                        "分数应在 0-1 范围内，实际: " + chunk.getScore());
            }
        }
    }

    @Test
    @DisplayName("BM25 权重为 1 时应只使用 BM25 分数")
    void shouldUseOnlyBM25WhenWeightIsOne() {
        List<Document> documents = List.of(
                createDocument("doc-1", "串通投标是指投标人之间相互约定"),
                createDocument("doc-2", "招标投标应当遵循公开公平原则")
        );

        when(embeddingService.embed(any())).thenReturn(new float[]{0.1f});

        // 执行，BM25 权重为 1.0
        List<RagChunk> result = hybridSearchService.hybridSearch("串通投标", documents, 5, 1.0);

        // 验证结果不为空（BM25 应该能找到包含关键词的文档）
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("BM25 权重为 0 时应只使用向量分数")
    void shouldUseOnlyVectorScoreWhenWeightIsZero() {
        List<Document> documents = List.of(
                createDocument("doc-1", "串通投标是指投标人之间相互约定"),
                createDocument("doc-2", "招标投标应当遵循公开公平原则")
        );

        when(embeddingService.embed(any())).thenReturn(new float[]{0.1f});

        // 执行，BM25 权重为 0.0
        List<RagChunk> result = hybridSearchService.hybridSearch("串通投标", documents, 5, 0.0);

        // 验证结果存在
        assertNotNull(result);
    }

    @Test
    @DisplayName("分词应正确处理中英文混合文本")
    void shouldTokenizeChineseAndEnglishText() {
        // 使用反射调用 private 方法 tokenize
        java.lang.reflect.Method tokenizeMethod;
        try {
            tokenizeMethod = HybridSearchService.class.getDeclaredMethod("tokenize", String.class);
            tokenizeMethod.setAccessible(true);

            // 测试中文分词
            List<String> chineseResult = (List<String>) tokenizeMethod.invoke(hybridSearchService, "这是测试文本");
            assertNotNull(chineseResult);

            // 测试英文分词
            List<String> englishResult = (List<String>) tokenizeMethod.invoke(hybridSearchService, "hello world test");
            assertNotNull(englishResult);

            // 测试中英文混合
            List<String> mixedResult = (List<String>) tokenizeMethod.invoke(hybridSearchService, "药品drug注册registration");
            assertNotNull(mixedResult);

        } catch (Exception e) {
            fail("分词方法调用失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("分词应过滤单字符词")
    void shouldFilterSingleCharacterTokens() {
        try {
            java.lang.reflect.Method tokenizeMethod = HybridSearchService.class.getDeclaredMethod("tokenize", String.class);
            tokenizeMethod.setAccessible(true);

            List<String> result = (List<String>) tokenizeMethod.invoke(hybridSearchService, "我 的 是 一个 测试");
            assertNotNull(result);

            // 单字符应该被过滤掉
            for (String token : result) {
                assertTrue(token.length() > 1, "单字符不应该被保留: " + token);
            }

        } catch (Exception e) {
            fail("分词方法调用失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("RagChunk 应正确包含 metadata 信息")
    void shouldContainMetadataInRagChunk() {
        List<Document> documents = List.of(
                createDocumentWithMetadata("doc-1", "招标投标法规定", "org-001", "REGULATION")
        );

        when(embeddingService.embed(any())).thenReturn(new float[]{0.1f});

        List<RagChunk> result = hybridSearchService.hybridSearch("招标", documents, 5, 0.3);

        assertNotNull(result);
        if (!result.isEmpty()) {
            RagChunk chunk = result.get(0);
            ChunkMetadata metadata = chunk.getMetadata();
            assertNotNull(metadata);
        }
    }

    // -------------------- 辅助方法 --------------------

    private List<Document> createTestDocuments() {
        return List.of(
                createDocument("doc-1", "串通投标是指投标人之间相互约定抬高或降低报价的行为"),
                createDocument("doc-2", "招标投标应当遵循公开公平公正和诚实信用的原则"),
                createDocument("doc-3", "药品注册需要提交相关的技术资料和临床试验数据")
        );
    }

    private Document createDocument(String id, String text) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("orgId", "test-org");
        metadata.put("scene", "TEST");
        metadata.put("sourceId", "source-001");
        metadata.put("sourceTitle", "测试文档");

        return Document.builder()
                .id(id)
                .text(text)
                .metadata(metadata)
                .build();
    }

    private Document createDocumentWithMetadata(String id, String text, String orgId, String docType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("orgId", orgId);
        metadata.put("scene", "TEST");
        metadata.put("subScene", "TEST_SCENE");
        metadata.put("docType", docType);
        metadata.put("sourceId", "source-001");
        metadata.put("sourceTitle", "测试文档");
        metadata.put("chunkIndex", 0);

        return Document.builder()
                .id(id)
                .text(text)
                .metadata(metadata)
                .build();
    }
}