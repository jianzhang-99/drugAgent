package com.liang.drugagent.shared.llm.benchmark;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liang.drugagent.agent.chat.LLMChatService;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.llm.LlmProviderType;
import com.liang.drugagent.shared.llm.ModelInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ModelBenchmarkService 单元测试。
 *
 * <p>测试模型评测服务的各项功能：
 * <ul>
 *   <li>单模型评测成功/失败</li>
 *   <li>批量评测多模型</li>
 *   <li>评测所有可用模型</li>
 *   <li>Provider解析逻辑</li>
 *   <li>Token估算逻辑</li>
 *   <li>Prompt模板获取</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ModelBenchmarkServiceTest {

    @Mock
    private ModelBenchmarkResultMapper benchmarkResultMapper;

    @Mock
    private LLMChatService llmChatService;

    private ModelBenchmarkService modelBenchmarkService;

    @BeforeEach
    void setUp() {
        modelBenchmarkService = new ModelBenchmarkService(benchmarkResultMapper, llmChatService);
    }

    @Nested
    @DisplayName("Case 1: 单模型评测成功")
    class SingleModelBenchmarkSuccess {

        @Test
        @DisplayName("Mock LLM返回正常结果 -> 验证结果保存成功")
        void shouldSaveResultWhenLlmReturnsNormalResponse() {
            // Given
            String modelName = "MiniMax-M2.7-highspeed";
            String prompt = "请介绍一下你自己";
            String expectedResponse = "我是MiniMax M2.7高速模型...";

            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq(modelName)))
                    .thenReturn(expectedResponse);
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            ModelBenchmarkResp result = modelBenchmarkService.runBenchmark(modelName, prompt);

            // Then
            assertNotNull(result);
            assertTrue(result.getSuccess());
            assertEquals(modelName, result.getModelName());
            assertEquals(expectedResponse, result.getResponse());
            assertNotNull(result.getProvider());
            assertNotNull(result.getBenchmarkTime());
            assertNull(result.getErrorMessage());

            // 验证Provider是MINIMAX
            assertEquals(LlmProviderType.MINIMAX.name(), result.getProvider());

            // 验证Token估算：response.length() / 2
            assertEquals(expectedResponse.length() / 2, result.getTokensUsed());

            // 验证结果已保存
            verify(benchmarkResultMapper, times(1)).insert(any(ModelBenchmarkResult.class));
        }
    }

    @Nested
    @DisplayName("Case 2: 单模型评测失败")
    class SingleModelBenchmarkFailure {

        @Test
        @DisplayName("LLM调用异常 -> 验证错误信息记录")
        void shouldRecordErrorWhenLlmThrowsException() {
            // Given
            String modelName = "MiniMax-M2.7-highspeed";
            String prompt = "请介绍一下你自己";
            String errorMessage = "LLM服务不可用";

            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq(modelName)))
                    .thenThrow(new RuntimeException(errorMessage));
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            ModelBenchmarkResp result = modelBenchmarkService.runBenchmark(modelName, prompt);

            // Then
            assertNotNull(result);
            assertFalse(result.getSuccess());
            assertEquals(modelName, result.getModelName());
            assertEquals(errorMessage, result.getErrorMessage());
            assertNull(result.getResponse());
            assertEquals(0, result.getTokensUsed());
            assertNotNull(result.getProvider());

            // 验证结果已保存（包含错误信息）
            ArgumentCaptor<ModelBenchmarkResult> captor = ArgumentCaptor.forClass(ModelBenchmarkResult.class);
            verify(benchmarkResultMapper).insert(captor.capture());
            ModelBenchmarkResult savedResult = captor.getValue();
            assertFalse(savedResult.getSuccess());
            assertEquals(errorMessage, savedResult.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("Case 3: 批量评测多模型")
    class BatchBenchmarkMultipleModels {

        @Test
        @DisplayName("指定modelNames列表 -> 验证按列表评测")
        void shouldBenchmarkSpecifiedModels() {
            // Given
            List<String> modelNames = List.of("MiniMax-M2.7-highspeed", "qwen-turbo");
            String prompt = "请介绍一下你自己";
            String response1 = "我是MiniMax模型";
            String response2 = "我是通义千问模型";

            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq("MiniMax-M2.7-highspeed")))
                    .thenReturn(response1);
            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq("qwen-turbo")))
                    .thenReturn(response2);
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            List<ModelBenchmarkResp> results = modelBenchmarkService.runBenchmarksByModels(modelNames, prompt);

            // Then
            assertNotNull(results);
            assertEquals(2, results.size());
            assertEquals("MiniMax-M2.7-highspeed", results.get(0).getModelName());
            assertEquals("qwen-turbo", results.get(1).getModelName());

            // 验证每个模型都被调用了一次
            verify(llmChatService, times(2)).chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), anyString());
            verify(benchmarkResultMapper, times(2)).insert(any(ModelBenchmarkResult.class));
        }

        @Test
        @DisplayName("列表中包含null或空白字符串 -> 应跳过")
        void shouldSkipNullOrBlankModelNames() {
            // Given
            List<String> modelNames = Arrays.asList("MiniMax-M2.7-highspeed", null, "", "  ", "qwen-turbo");
            String prompt = "请介绍一下你自己";

            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), any(), any()))
                    .thenReturn("response");
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            List<ModelBenchmarkResp> results = modelBenchmarkService.runBenchmarksByModels(modelNames, prompt);

            // Then
            assertNotNull(results);
            assertEquals(2, results.size());
            // 只调用了2次（跳过了null和空白字符串）
            verify(llmChatService, times(2)).chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), any(), any());
        }
    }

    @Nested
    @DisplayName("Case 4: 评测所有可用模型")
    class BenchmarkAllAvailableModels {

        @Test
        @DisplayName("modelNames为空 -> 验证调用getAvailableModels")
        void shouldCallGetAvailableModelsWhenModelNamesEmpty() {
            // Given
            String prompt = "请介绍一下你自己";
            List<ModelInfo> availableModels = List.of(
                    ModelInfo.builder().model("minimax").defaultModelName("MiniMax-M2.7-highspeed").available(true).build(),
                    ModelInfo.builder().model("dashscope").defaultModelName("qwen-turbo").available(true).build()
            );

            when(llmChatService.getAvailableModels()).thenReturn(availableModels);
            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), anyString()))
                    .thenReturn("response");
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            List<ModelBenchmarkResp> results = modelBenchmarkService.runAllBenchmarks(prompt);

            // Then
            assertNotNull(results);
            assertEquals(2, results.size());
            verify(llmChatService, times(1)).getAvailableModels();
            verify(llmChatService, times(2)).chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), anyString());
        }

        @Test
        @DisplayName("可用模型中包含不可用模型 -> 应跳过")
        void shouldSkipUnavailableModels() {
            // Given
            String prompt = "请介绍一下你自己";
            List<ModelInfo> availableModels = List.of(
                    ModelInfo.builder().model("minimax").defaultModelName("MiniMax-M2.7-highspeed").available(true).build(),
                    ModelInfo.builder().model("dashscope").defaultModelName("qwen-turbo").available(false).build()
            );

            when(llmChatService.getAvailableModels()).thenReturn(availableModels);
            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq("MiniMax-M2.7-highspeed")))
                    .thenReturn("response");
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            List<ModelBenchmarkResp> results = modelBenchmarkService.runAllBenchmarks(prompt);

            // Then
            assertNotNull(results);
            assertEquals(1, results.size());
            verify(llmChatService, times(1)).getAvailableModels();
            verify(llmChatService, times(1)).chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq("MiniMax-M2.7-highspeed"));
        }
    }

    @Nested
    @DisplayName("Case 5 & 6: Provider解析")
    class ProviderResolution {

        @Test
        @DisplayName("minimax模型 -> 期望 MINIMAX")
        void shouldResolveMinimaxProvider() {
            // Given
            String prompt = "请介绍一下你自己";
            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq("MiniMax-M2.7-highspeed")))
                    .thenReturn("response");
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            ModelBenchmarkResp result = modelBenchmarkService.runBenchmark("MiniMax-M2.7-highspeed", prompt);

            // Then
            assertEquals(LlmProviderType.MINIMAX.name(), result.getProvider());
        }

        @Test
        @DisplayName("abab模型 -> 期望 MINIMAX")
        void shouldResolveAbabProvider() {
            // Given
            String prompt = "请介绍一下你自己";
            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq("abab6.5s")))
                    .thenReturn("response");
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            ModelBenchmarkResp result = modelBenchmarkService.runBenchmark("abab6.5s", prompt);

            // Then
            assertEquals(LlmProviderType.MINIMAX.name(), result.getProvider());
        }

        @Test
        @DisplayName("qwen模型 -> 期望 DASHSCOPE")
        void shouldResolveQwenProvider() {
            // Given
            String prompt = "请介绍一下你自己";
            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq("qwen-turbo")))
                    .thenReturn("response");
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            ModelBenchmarkResp result = modelBenchmarkService.runBenchmark("qwen-turbo", prompt);

            // Then
            assertEquals(LlmProviderType.DASHSCOPE.name(), result.getProvider());
        }

        @Test
        @DisplayName("未知模型 -> 期望 DASHSCOPE (默认)")
        void shouldResolveUnknownModelToDashscope() {
            // Given
            String prompt = "请介绍一下你自己";
            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq("unknown-model")))
                    .thenReturn("response");
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            ModelBenchmarkResp result = modelBenchmarkService.runBenchmark("unknown-model", prompt);

            // Then
            assertEquals(LlmProviderType.DASHSCOPE.name(), result.getProvider());
        }

        @Test
        @DisplayName("null模型 -> 期望 DASHSCOPE (默认)")
        void shouldResolveNullModelToDashscope() {
            // Given
            String prompt = "请介绍一下你自己";
            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), isNull()))
                    .thenReturn("response");
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            ModelBenchmarkResp result = modelBenchmarkService.runBenchmark(null, prompt);

            // Then
            assertEquals(LlmProviderType.DASHSCOPE.name(), result.getProvider());
        }
    }

    @Nested
    @DisplayName("Case 7: Token估算")
    class TokenEstimation {

        @Test
        @DisplayName("验证估算逻辑正确性 - content.length() / 2")
        void shouldEstimateTokensCorrectly() {
            // Given
            String prompt = "请介绍一下你自己";
            String content = "这是一个测试回复内容"; // 10个字符

            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq("MiniMax-M2.7-highspeed")))
                    .thenReturn(content);
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            ModelBenchmarkResp result = modelBenchmarkService.runBenchmark("MiniMax-M2.7-highspeed", prompt);

            // Then
            assertEquals(content.length() / 2, result.getTokensUsed());
            assertEquals(5, result.getTokensUsed());
        }

        @Test
        @DisplayName("空内容 -> 期望 0 tokens")
        void shouldReturnZeroTokensForEmptyContent() {
            // Given
            String prompt = "请介绍一下你自己";

            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq("MiniMax-M2.7-highspeed")))
                    .thenReturn("");
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            ModelBenchmarkResp result = modelBenchmarkService.runBenchmark("MiniMax-M2.7-highspeed", prompt);

            // Then
            assertEquals(0, result.getTokensUsed());
        }

        @Test
        @DisplayName("null内容 -> 期望 0 tokens")
        void shouldReturnZeroTokensForNullContent() {
            // Given
            String prompt = "请介绍一下你自己";

            when(llmChatService.chatWithScene(eq(prompt), eq(SceneEnum.DEFAULT), anyString(), eq("MiniMax-M2.7-highspeed")))
                    .thenReturn(null);
            when(benchmarkResultMapper.insert(any(ModelBenchmarkResult.class)))
                    .thenReturn(1);

            // When
            ModelBenchmarkResp result = modelBenchmarkService.runBenchmark("MiniMax-M2.7-highspeed", prompt);

            // Then
            assertEquals(0, result.getTokensUsed());
        }
    }

    @Nested
    @DisplayName("Case 8: 获取Prompt模板")
    class GetPromptTemplates {

        @Test
        @DisplayName("验证返回4个预设模板")
        void shouldReturnFourPresetTemplates() {
            // When
            List<PromptTemplateResp> templates = modelBenchmarkService.getPromptTemplates();

            // Then
            assertNotNull(templates);
            assertEquals(4, templates.size());

            // 验证模板ID
            List<String> ids = templates.stream().map(PromptTemplateResp::getId).toList();
            assertTrue(ids.contains("self-intro"));
            assertTrue(ids.contains("drug-knowledge"));
            assertTrue(ids.contains("regulation-consult"));
            assertTrue(ids.contains("risk-identification"));

            // 验证模板名称
            List<String> names = templates.stream().map(PromptTemplateResp::getName).toList();
            assertTrue(names.contains("你好/自我介绍"));
            assertTrue(names.contains("药品知识问答"));
            assertTrue(names.contains("法规咨询"));
            assertTrue(names.contains("风险识别"));

            // 验证每个模板都有content
            for (PromptTemplateResp template : templates) {
                assertNotNull(template.getContent());
                assertFalse(template.getContent().isBlank());
            }
        }
    }

    @Nested
    @DisplayName("获取历史评测记录")
    class GetResults {

        @Test
        @DisplayName("验证分页查询正常")
        void shouldQueryResultsWithPagination() {
            // Given
            int page = 1;
            int pageSize = 10;
            Page<ModelBenchmarkResult> mockResult = new Page<>(page, pageSize);

            doReturn(mockResult).when(benchmarkResultMapper).selectPage(any(Page.class), any());

            // When
            IPage<ModelBenchmarkResult> result = modelBenchmarkService.getResults(page, pageSize);

            // Then
            assertNotNull(result);
            verify(benchmarkResultMapper, times(1)).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("获取可用模型列表")
    class GetAvailableModels {

        @Test
        @DisplayName("验证返回LLM服务可用模型列表")
        void shouldReturnAvailableModelsFromLlmService() {
            // Given
            List<ModelInfo> expectedModels = List.of(
                    ModelInfo.builder().model("minimax").defaultModelName("MiniMax-M2.7-highspeed").available(true).build(),
                    ModelInfo.builder().model("dashscope").defaultModelName("qwen-turbo").available(true).build()
            );
            when(llmChatService.getAvailableModels()).thenReturn(expectedModels);

            // When
            List<ModelInfo> result = modelBenchmarkService.getAvailableModels();

            // Then
            assertNotNull(result);
            assertEquals(2, result.size());
            verify(llmChatService, times(1)).getAvailableModels();
        }
    }
}
