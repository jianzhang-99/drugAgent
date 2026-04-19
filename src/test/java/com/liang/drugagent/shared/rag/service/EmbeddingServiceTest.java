package com.liang.drugagent.shared.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EmbeddingService 单元测试。
 *
 * <p>验证文本到向量的转换逻辑：
 * <ul>
 *   <li>单条文本 embedding 正常返回向量</li>
 *   <li>空文本或空白字符串抛出异常</li>
 *   <li>null 输入抛出异常</li>
 *   <li>批量 embedding 正常返回向量列表</li>
 *   <li>向量维度一致性</li>
 * </ul>
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingService 向量化服务单元测试")
class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(embeddingModel);
    }

    @Test
    @DisplayName("正常文本应返回 float[] 向量")
    void shouldReturnVectorWhenTextIsValid() {
        // 准备模拟返回
        float[] expectedVector = new float[]{0.1f, 0.2f, 0.3f, 0.4f};
        Embedding embedding = new Embedding(expectedVector, 0);
        EmbeddingResponse response = new EmbeddingResponse(
                List.of(embedding),
                new EmbeddingResponseMetadata()
        );
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

        // 执行
        float[] result = embeddingService.embed("测试文本");

        // 验证
        assertNotNull(result);
        assertEquals(4, result.length);
        assertArrayEquals(expectedVector, result);
        verify(embeddingModel).call(any(EmbeddingRequest.class));
    }

    @Test
    @DisplayName("空白字符串应抛出 IllegalArgumentException")
    void shouldThrowExceptionWhenTextIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> embeddingService.embed("   "));
        verify(embeddingModel, never()).call(any());
    }

    @Test
    @DisplayName("null 输入应抛出 IllegalArgumentException")
    void shouldThrowExceptionWhenTextIsNull() {
        assertThrows(IllegalArgumentException.class, () -> embeddingService.embed(null));
        verify(embeddingModel, never()).call(any());
    }

    @Test
    @DisplayName("空字符串应抛出 IllegalArgumentException")
    void shouldThrowExceptionWhenTextIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> embeddingService.embed(""));
        verify(embeddingModel, never()).call(any());
    }

    @Test
    @DisplayName("批量 embedding 应返回向量列表")
    void shouldReturnVectorListWhenBatchTextProvided() {
        // 准备模拟返回
        float[] vec1 = new float[]{0.1f, 0.2f};
        float[] vec2 = new float[]{0.3f, 0.4f};
        Embedding embedding1 = new Embedding(vec1, 0);
        Embedding embedding2 = new Embedding(vec2, 1);
        EmbeddingResponse response = new EmbeddingResponse(
                List.of(embedding1, embedding2),
                new EmbeddingResponseMetadata()
        );
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

        // 执行
        List<float[]> result = embeddingService.embedBatch(List.of("文本1", "文本2"));

        // 验证
        assertNotNull(result);
        assertEquals(2, result.size());
        assertArrayEquals(vec1, result.get(0));
        assertArrayEquals(vec2, result.get(1));
    }

    @Test
    @DisplayName("空列表批量 embedding 应返回空列表")
    void shouldReturnEmptyListWhenBatchIsEmpty() {
        List<float[]> result = embeddingService.embedBatch(List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(embeddingModel, never()).call(any());
    }

    @Test
    @DisplayName("null 批量 embedding 应返回空列表")
    void shouldReturnEmptyListWhenBatchIsNull() {
        List<float[]> result = embeddingService.embedBatch(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(embeddingModel, never()).call(any());
    }

    @Test
    @DisplayName("Embedding API 返回空结果应抛出 RuntimeException")
    void shouldThrowExceptionWhenEmbeddingReturnsEmpty() {
        EmbeddingResponse emptyResponse = new EmbeddingResponse(
                List.of(),
                new EmbeddingResponseMetadata()
        );
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(emptyResponse);

        assertThrows(RuntimeException.class, () -> embeddingService.embed("测试文本"));
    }

    @Test
    @DisplayName("不同文本应返回相同维度向量")
    void shouldReturnSameDimensionVectorsForDifferentTexts() {
        float[] vec1 = new float[]{0.1f, 0.2f, 0.3f};
        float[] vec2 = new float[]{0.4f, 0.5f, 0.6f};
        Embedding embedding1 = new Embedding(vec1, 0);
        Embedding embedding2 = new Embedding(vec2, 1);

        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(embedding1), new EmbeddingResponseMetadata()))
                .thenReturn(new EmbeddingResponse(List.of(embedding2), new EmbeddingResponseMetadata()));

        float[] result1 = embeddingService.embed("文本1");
        float[] result2 = embeddingService.embed("文本2");

        assertEquals(result1.length, result2.length);
    }
}