package com.liang.drugagent.shared.llm.benchmark;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liang.drugagent.shared.llm.ModelInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ModelBenchmarkController 集成测试。
 *
 * <p>使用 @WebMvcTest + MockMvc 切片测试，验证：
 * <ul>
 *   <li>POST /api/benchmark/run - 单模型评测</li>
 *   <li>POST /api/benchmark/run-all - 批量评测（单模型/多模型/全部）</li>
 *   <li>GET /api/benchmark/results - 分页查询历史记录</li>
 *   <li>GET /api/benchmark/models - 获取模型列表</li>
 *   <li>GET /api/benchmark/prompt-templates - 获取Prompt模板</li>
 * </ul>
 */
@WebMvcTest(controllers = ModelBenchmarkController.class)
@Import(ModelBenchmarkController.class)
@DisplayName("ModelBenchmarkController 集成测试")
class ModelBenchmarkControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelBenchmarkService benchmarkService;

    @MockBean
    private org.springframework.ai.vectorstore.VectorStore vectorStore;

    // ========== POST /api/benchmark/run - 单模型评测 ==========

    @Test
    @DisplayName("Case 1: POST /api/benchmark/run - 单模型评测 -> 验证返回200和结果")
    void shouldReturn200WhenRunSingleModelBenchmark() throws Exception {
        String modelName = "MiniMax-M2.7-highspeed";
        String prompt = "请介绍一下你自己";

        ModelBenchmarkResp mockResp = ModelBenchmarkResp.builder()
                .modelName(modelName)
                .provider("MINIMAX")
                .responseTimeMs(1500L)
                .tokensUsed(500)
                .success(true)
                .errorMessage(null)
                .benchmarkTime(LocalDateTime.now())
                .response("我是MiniMax开发的AI助手...")
                .build();

        when(benchmarkService.runBenchmark(eq(modelName), eq(prompt))).thenReturn(mockResp);

        mockMvc.perform(post("/api/benchmark/run")
                        .param("modelName", modelName)
                        .param("prompt", prompt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.modelName").value(modelName))
                .andExpect(jsonPath("$.data.provider").value("MINIMAX"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.response").exists());

        verify(benchmarkService).runBenchmark(modelName, prompt);
    }

    // ========== POST /api/benchmark/run-all - 单模型评测（指定modelName）==========

    @Test
    @DisplayName("Case 2: POST /api/benchmark/run-all 单模型 - 指定modelName -> 验证返回列表")
    void shouldReturnListWhenRunBenchmarkWithSingleModelName() throws Exception {
        String modelName = "qwen-turbo";
        String prompt = "测试Prompt";

        ModelBenchmarkResp mockResp = ModelBenchmarkResp.builder()
                .modelName(modelName)
                .provider("DASHSCOPE")
                .responseTimeMs(1200L)
                .tokensUsed(300)
                .success(true)
                .errorMessage(null)
                .benchmarkTime(LocalDateTime.now())
                .response("我是通义千问...")
                .build();

        when(benchmarkService.runBenchmark(eq(modelName), eq(prompt))).thenReturn(mockResp);

        String requestBody = """
                {
                    "modelName": "qwen-turbo",
                    "prompt": "测试Prompt"
                }
                """;

        mockMvc.perform(post("/api/benchmark/run-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].modelName").value(modelName));

        verify(benchmarkService).runBenchmark(modelName, prompt);
    }

    // ========== POST /api/benchmark/run-all - 多模型评测（指定modelNames列表）==========

    @Test
    @DisplayName("Case 3: POST /api/benchmark/run-all 多模型 - 指定modelNames列表 -> 验证列表长度")
    void shouldReturnListWhenRunBenchmarksWithMultipleModelNames() throws Exception {
        List<String> modelNames = List.of("MiniMax-M2.7-highspeed", "qwen-turbo", "gpt-4o");

        ModelBenchmarkResp mockResp1 = ModelBenchmarkResp.builder()
                .modelName("MiniMax-M2.7-highspeed")
                .provider("MINIMAX")
                .responseTimeMs(1500L)
                .tokensUsed(500)
                .success(true)
                .benchmarkTime(LocalDateTime.now())
                .response("MiniMax响应")
                .build();

        ModelBenchmarkResp mockResp2 = ModelBenchmarkResp.builder()
                .modelName("qwen-turbo")
                .provider("DASHSCOPE")
                .responseTimeMs(1200L)
                .tokensUsed(300)
                .success(true)
                .benchmarkTime(LocalDateTime.now())
                .response("qwen响应")
                .build();

        ModelBenchmarkResp mockResp3 = ModelBenchmarkResp.builder()
                .modelName("gpt-4o")
                .provider("DASHSCOPE")
                .responseTimeMs(2000L)
                .tokensUsed(800)
                .success(true)
                .benchmarkTime(LocalDateTime.now())
                .response("gpt响应")
                .build();

        when(benchmarkService.runBenchmarksByModels(eq(modelNames), eq("多模型测试")))
                .thenReturn(List.of(mockResp1, mockResp2, mockResp3));

        String requestBody = """
                {
                    "modelNames": ["MiniMax-M2.7-highspeed", "qwen-turbo", "gpt-4o"],
                    "prompt": "多模型测试"
                }
                """;

        mockMvc.perform(post("/api/benchmark/run-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));

        verify(benchmarkService).runBenchmarksByModels(modelNames, "多模型测试");
    }

    // ========== POST /api/benchmark/run-all - 全部模型评测（空参数）==========

    @Test
    @DisplayName("Case 4: POST /api/benchmark/run-all 全部模型 - 空参数 -> 验证调用所有模型")
    void shouldRunAllBenchmarksWhenNoModelSpecified() throws Exception {
        String prompt = "测试全部模型";

        ModelBenchmarkResp mockResp1 = ModelBenchmarkResp.builder()
                .modelName("MiniMax-M2.7-highspeed")
                .provider("MINIMAX")
                .responseTimeMs(1500L)
                .tokensUsed(500)
                .success(true)
                .benchmarkTime(LocalDateTime.now())
                .response("响应1")
                .build();

        ModelBenchmarkResp mockResp2 = ModelBenchmarkResp.builder()
                .modelName("qwen-turbo")
                .provider("DASHSCOPE")
                .responseTimeMs(1200L)
                .tokensUsed(300)
                .success(true)
                .benchmarkTime(LocalDateTime.now())
                .response("响应2")
                .build();

        when(benchmarkService.runAllBenchmarks(eq(prompt))).thenReturn(List.of(mockResp1, mockResp2));

        String requestBody = """
                {
                    "prompt": "测试全部模型"
                }
                """;

        mockMvc.perform(post("/api/benchmark/run-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));

        verify(benchmarkService).runAllBenchmarks(prompt);
    }

    // ========== GET /api/benchmark/results - 分页查询 ==========

    @Test
    @DisplayName("Case 5: GET /api/benchmark/results - 分页查询 -> 验证返回分页结果")
    void shouldReturnPaginatedResultsWhenGetResults() throws Exception {
        ModelBenchmarkResult result1 = ModelBenchmarkResult.builder()
                .id(1L)
                .modelName("MiniMax-M2.7-highspeed")
                .provider("MINIMAX")
                .prompt("测试Prompt")
                .responseTimeMs(1500L)
                .tokensUsed(500)
                .success(true)
                .benchmarkTime(LocalDateTime.now())
                .response("响应内容")
                .build();

        ModelBenchmarkResult result2 = ModelBenchmarkResult.builder()
                .id(2L)
                .modelName("qwen-turbo")
                .provider("DASHSCOPE")
                .prompt("测试Prompt")
                .responseTimeMs(1200L)
                .tokensUsed(300)
                .success(true)
                .benchmarkTime(LocalDateTime.now())
                .response("响应内容2")
                .build();

        Page<ModelBenchmarkResult> page = new Page<>(1, 10);
        page.setRecords(List.of(result1, result2));
        page.setTotal(2);

        IPage<ModelBenchmarkResult> mockPage = page;

        when(benchmarkService.getResults(eq(1), eq(10))).thenReturn(mockPage);

        mockMvc.perform(get("/api/benchmark/results")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(2));

        verify(benchmarkService).getResults(1, 10);
    }

    // ========== GET /api/benchmark/models - 获取模型列表 ==========

    @Test
    @DisplayName("Case 6: GET /api/benchmark/models - 获取模型列表 -> 验证返回模型信息")
    void shouldReturnModelListWhenGetModels() throws Exception {
        List<ModelInfo> mockModels = List.of(
                ModelInfo.builder()
                        .model("minimax")
                        .name("MiniMax")
                        .defaultModelName("MiniMax-M2.7-highspeed")
                        .available(true)
                        .isDefault(true)
                        .build(),
                ModelInfo.builder()
                        .model("dashscope")
                        .name("阿里云通义")
                        .defaultModelName("qwen-turbo")
                        .available(true)
                        .isDefault(false)
                        .build()
        );

        when(benchmarkService.getAvailableModels()).thenReturn(mockModels);

        mockMvc.perform(get("/api/benchmark/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].model").value("minimax"))
                .andExpect(jsonPath("$.data[0].defaultModelName").value("MiniMax-M2.7-highspeed"))
                .andExpect(jsonPath("$.data[0].available").value(true));

        verify(benchmarkService).getAvailableModels();
    }

    // ========== GET /api/benchmark/prompt-templates - 获取模板 ==========

    @Test
    @DisplayName("Case 7: GET /api/benchmark/prompt-templates - 获取模板 -> 验证返回4个模板")
    void shouldReturn4TemplatesWhenGetPromptTemplates() throws Exception {
        List<PromptTemplateResp> mockTemplates = List.of(
                PromptTemplateResp.builder()
                        .id("self-intro")
                        .name("你好/自我介绍")
                        .description("测试模型的基础对话和自我介绍能力")
                        .content("请介绍一下你自己，包括你的名称、能力和擅长领域。")
                        .build(),
                PromptTemplateResp.builder()
                        .id("drug-knowledge")
                        .name("药品知识问答")
                        .description("测试模型在医药领域的专业知识")
                        .content("请介绍一下常用的降压药物分类及其代表药物。")
                        .build(),
                PromptTemplateResp.builder()
                        .id("regulation-consult")
                        .name("法规咨询")
                        .description("测试模型对医药法规的理解能力")
                        .content("请介绍一下《药品管理法》中关于药品上市许可持有人制度的主要内容。")
                        .build(),
                PromptTemplateResp.builder()
                        .id("risk-identification")
                        .name("风险识别")
                        .description("测试模型对风险识别和评估的能力")
                        .content("在某药品招标采购中，如何识别围标、串标风险？请列出关键判断标准。")
                        .build()
        );

        when(benchmarkService.getPromptTemplates()).thenReturn(mockTemplates);

        mockMvc.perform(get("/api/benchmark/prompt-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].id").value("self-intro"))
                .andExpect(jsonPath("$.data[1].id").value("drug-knowledge"))
                .andExpect(jsonPath("$.data[2].id").value("regulation-consult"))
                .andExpect(jsonPath("$.data[3].id").value("risk-identification"));

        verify(benchmarkService).getPromptTemplates();
    }
}