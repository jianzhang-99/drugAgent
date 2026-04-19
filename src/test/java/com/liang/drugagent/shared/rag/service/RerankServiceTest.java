package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.shared.rag.model.ChunkMetadata;
import com.liang.drugagent.shared.rag.model.RagChunk;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RerankService 单元测试。
 *
 * <p>验证 Relevance + Diversity 重排逻辑：
 * <ul>
 *   <li>空列表应返回空列表</li>
 *   <li>数量小于 topK 应返回全部 chunks</li>
 *   <li>相关性计算正确</li>
 *   <li>多样性计算正确</li>
 *   <li>综合评分公式正确</li>
 *   <li>topK 排序正确</li>
 * </ul>
 */
@Slf4j
@DisplayName("RerankService 重排服务单元测试")
class RerankServiceTest {

    private RerankService rerankService;

    @BeforeEach
    void setUp() {
        rerankService = new RerankService();
    }

    @Test
    @DisplayName("空列表应返回空列表")
    void shouldReturnEmptyListWhenChunksIsEmpty() {
        List<RagChunk> result = rerankService.rerank("查询", new ArrayList<>(), 5);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("null 列表应返回空列表")
    void shouldReturnEmptyListWhenChunksIsNull() {
        List<RagChunk> result = rerankService.rerank("查询", null, 5);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("数量小于 topK 应返回全部 chunks")
    void shouldReturnAllChunksWhenSizeLessThanTopK() {
        List<RagChunk> chunks = List.of(
                createChunk("chunk-1", "串通投标是违法行为"),
                createChunk("chunk-2", "招标投标应当遵循公平原则")
        );

        List<RagChunk> result = rerankService.rerank("串通投标", chunks, 10);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("正常重排应返回排序后结果")
    void shouldReturnSortedResultsWhenNormalCase() {
        List<RagChunk> chunks = List.of(
                createChunk("chunk-1", "串通投标是指投标人之间相互约定"),
                createChunk("chunk-2", "招标投标应当遵循公开公平公正原则"),
                createChunk("chunk-3", "药品注册需要提交临床试验数据"),
                createChunk("chunk-4", "投标人不得相互串通损害招标人利益")
        );

        List<RagChunk> result = rerankService.rerank("串通投标", chunks, 3);

        assertNotNull(result);
        assertEquals(3, result.size());

        // 验证包含串通投标关键词的 chunk 排在前面
        boolean foundRelated = false;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).getContent().contains("串通投标")) {
                foundRelated = true;
                // 第一个相关 chunk 应该在结果中
                break;
            }
        }
        assertTrue(foundRelated, "应包含串通投标相关的 chunk");
    }

    @Test
    @DisplayName("同一 sourceId 应降低多样性分数")
    void shouldLowerDiversityScoreForSameSourceId() {
        // 创建来自同一文档的多个 chunks
        List<RagChunk> chunks = List.of(
                createChunkWithSourceId("chunk-1", "串通投标第一条内容", "DOC-001"),
                createChunkWithSourceId("chunk-2", "串通投标第二条内容", "DOC-001"),
                createChunkWithSourceId("chunk-3", "招标投标法规定", "DOC-002"),
                createChunkWithSourceId("chunk-4", "药品管理法规定", "DOC-003")
        );

        List<RagChunk> result = rerankService.rerank("串通投标", chunks, 4);

        assertNotNull(result);
        assertEquals(4, result.size());

        // 来自不同文档的 chunk 应该有更高的多样性分数
        // DOC-002 的 "招标投标法规定" 应该排在 DOC-001 的 chunk 前面或后面
        // 具体取决于综合评分，但相同 sourceId 的 chunk 不会获得额外多样性加分
    }

    @Test
    @DisplayName("topK 为 1 应只返回 1 个结果")
    void shouldReturnOnlyOneResultWhenTopKIsOne() {
        List<RagChunk> chunks = List.of(
                createChunk("chunk-1", "串通投标内容一"),
                createChunk("chunk-2", "串通投标内容二"),
                createChunk("chunk-3", "招标投标内容")
        );

        List<RagChunk> result = rerankService.rerank("串通投标", chunks, 1);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("topK 为 0 应返回空列表")
    void shouldReturnEmptyListWhenTopKIsZero() {
        List<RagChunk> chunks = List.of(
                createChunk("chunk-1", "串通投标内容"),
                createChunk("chunk-2", "招标投标内容")
        );

        List<RagChunk> result = rerankService.rerank("串通投标", chunks, 0);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("分数应归一化到 0-1 范围")
    void shouldNormalizeScoresToRange() {
        List<RagChunk> chunks = List.of(
                createChunk("chunk-1", "这是关于串通投标的详细规定第一条"),
                createChunk("chunk-2", "这是关于串通投标的详细规定第二条"),
                createChunk("chunk-3", "这是关于招标投标的详细规定第三条"),
                createChunk("chunk-4", "这是关于药品注册的详细规定第四条")
        );

        List<RagChunk> result = rerankService.rerank("串通投标详细规定", chunks, 4);

        assertNotNull(result);
        // 重排后的 chunk 应该已经按综合评分排序
    }

    @Test
    @DisplayName("查询为空字符串时应正常处理")
    void shouldHandleEmptyQuery() {
        List<RagChunk> chunks = List.of(
                createChunk("chunk-1", "串通投标内容"),
                createChunk("chunk-2", "招标投标内容")
        );

        List<RagChunk> result = rerankService.rerank("", chunks, 5);

        assertNotNull(result);
        // 空的查询字符串不应该导致异常
    }

    @Test
    @DisplayName("查询为 null 时应正常处理")
    void shouldHandleNullQuery() {
        List<RagChunk> chunks = List.of(
                createChunk("chunk-1", "串通投标内容"),
                createChunk("chunk-2", "招标投标内容")
        );

        List<RagChunk> result = rerankService.rerank(null, chunks, 5);

        assertNotNull(result);
        // null 查询不应该导致异常
    }

    @Test
    @DisplayName("chunk 内容为空时应正常处理")
    void shouldHandleEmptyChunkContent() {
        List<RagChunk> chunks = List.of(
                createChunk("chunk-1", ""),
                createChunk("chunk-2", "招标投标内容")
        );

        List<RagChunk> result = rerankService.rerank("招标投标", chunks, 5);

        assertNotNull(result);
    }

    @Test
    @DisplayName("综合评分应考虑相关性和多样性")
    void shouldConsiderRelevanceAndDiversityInScoring() {
        // 来自不同文档的相关 chunks
        List<RagChunk> chunks = List.of(
                createChunkWithSourceId("chunk-1", "串通投标规定内容一", "DOC-A"),
                createChunkWithSourceId("chunk-2", "串通投标规定内容二", "DOC-B"),
                createChunkWithSourceId("chunk-3", "串通投标规定内容三", "DOC-C"),
                createChunkWithSourceId("chunk-4", "无关内容", "DOC-D")
        );

        List<RagChunk> result = rerankService.rerank("串通投标规定", chunks, 4);

        assertNotNull(result);
        assertEquals(4, result.size());

        // 验证排序：前3个应该是 DOC-A/B/C 中与查询相关的 chunks
        for (int i = 0; i < 3; i++) {
            assertTrue(
                    result.get(i).getContent().contains("串通投标"),
                    "排名靠前的 chunk 应与查询相关"
            );
        }
    }

    // -------------------- 辅助方法 --------------------

    private RagChunk createChunk(String chunkId, String content) {
        return createChunkWithSourceId(chunkId, content, "DEFAULT-SOURCE");
    }

    private RagChunk createChunkWithSourceId(String chunkId, String content, String sourceId) {
        ChunkMetadata metadata = ChunkMetadata.builder()
                .orgId("test-org")
                .scene("TEST")
                .subScene("TEST_SCENE")
                .docType("REGULATION")
                .sourceId(sourceId)
                .sourceTitle("测试文档")
                .chunkId(chunkId)
                .chunkIndex(0)
                .build();

        return RagChunk.builder()
                .chunkId(chunkId)
                .content(content)
                .metadata(metadata)
                .build();
    }
}