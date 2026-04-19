package com.liang.drugagent.shared.llm.benchmark;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liang.drugagent.agent.chat.LLMChatService;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.llm.LlmProviderType;
import com.liang.drugagent.shared.llm.ModelInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 模型评测服务。
 *
 * <p>负责执行模型性能评测、结果记录与查询，
 * 支持对多个模型进行基准测试并存储评测结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelBenchmarkService {

    private final ModelBenchmarkResultMapper benchmarkResultMapper;
    private final LLMChatService llmChatService;

    /**
     * 执行单模型评测。
     *
     * @param modelName 模型名称
     * @param prompt    评测Prompt
     * @return 评测响应
     */
    public ModelBenchmarkResp runBenchmark(String modelName, String prompt) {
        String sessionId = UUID.randomUUID().toString();
        log.info("[ModelBenchmarkService] 开始评测模型 - modelName={}, sessionId={}, prompt长度={}",
                modelName, sessionId, prompt != null ? prompt.length() : 0);

        long startTime = System.currentTimeMillis();
        String errorMessage = null;
        boolean success = false;
        int tokensUsed = 0;
        String provider = null;

        try {
            // 调用LLM服务执行评测
            String content = llmChatService.chatWithScene(prompt, SceneEnum.DEFAULT, sessionId, modelName);
            long responseTime = System.currentTimeMillis() - startTime;

            // 获取模型对应的Provider信息
            provider = resolveProvider(modelName);

            success = true;
            tokensUsed = estimateTokens(content);

            log.info("[ModelBenchmarkService] 模型评测成功 - modelName={}, responseTimeMs={}, tokensUsed={}",
                    modelName, responseTime, tokensUsed);

            // 构建响应对象
            ModelBenchmarkResp resp = ModelBenchmarkResp.builder()
                    .modelName(modelName)
                    .provider(provider)
                    .responseTimeMs(responseTime)
                    .tokensUsed(tokensUsed)
                    .success(success)
                    .errorMessage(null)
                    .benchmarkTime(LocalDateTime.now())
                    .response(content)
                    .build();

            // 保存评测结果到数据库
            saveBenchmarkResult(resp, prompt);

            return resp;

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            errorMessage = e.getMessage();
            provider = resolveProvider(modelName);

            log.error("[ModelBenchmarkService] 模型评测失败 - modelName={}, error={}", modelName, errorMessage, e);

            // 构建响应对象
            ModelBenchmarkResp resp = ModelBenchmarkResp.builder()
                    .modelName(modelName)
                    .provider(provider)
                    .responseTimeMs(responseTime)
                    .tokensUsed(0)
                    .success(false)
                    .errorMessage(errorMessage)
                    .benchmarkTime(LocalDateTime.now())
                    .response(null)
                    .build();

            // 保存评测结果到数据库
            saveBenchmarkResult(resp, prompt);

            return resp;
        }
    }

    /**
     * 保存评测结果到数据库。
     */
    private void saveBenchmarkResult(ModelBenchmarkResp resp, String prompt) {
        try {
            ModelBenchmarkResult entity = ModelBenchmarkResult.builder()
                    .modelName(resp.getModelName())
                    .provider(resp.getProvider())
                    .prompt(prompt)
                    .responseTimeMs(resp.getResponseTimeMs())
                    .tokensUsed(resp.getTokensUsed())
                    .success(resp.getSuccess())
                    .errorMessage(resp.getErrorMessage())
                    .benchmarkTime(resp.getBenchmarkTime())
                    .response(resp.getResponse())
                    .build();
            benchmarkResultMapper.insert(entity);
            log.info("[ModelBenchmarkService] 评测结果已保存 - modelName={}, id={}", resp.getModelName(), entity.getId());
        } catch (Exception e) {
            log.error("[ModelBenchmarkService] 保存评测结果失败 - modelName={}, error={}", resp.getModelName(), e.getMessage(), e);
        }
    }

    /**
     * 对所有可用模型执行评测。
     *
     * @param prompt 评测Prompt
     * @return 所有模型的评测结果列表
     */
    public List<ModelBenchmarkResp> runAllBenchmarks(String prompt) {
        log.info("[ModelBenchmarkService] 开始批量评测所有模型 - prompt长度={}", prompt != null ? prompt.length() : 0);

        List<ModelInfo> availableModels = llmChatService.getAvailableModels();
        List<ModelBenchmarkResp> results = new ArrayList<>();

        for (ModelInfo modelInfo : availableModels) {
            if (modelInfo.isAvailable()) {
                String modelNameToUse = modelInfo.getDefaultModelName() != null
                        ? modelInfo.getDefaultModelName()
                        : modelInfo.getModel();
                ModelBenchmarkResp result = runBenchmark(modelNameToUse, prompt);
                results.add(result);
            }
        }

        log.info("[ModelBenchmarkService] 批量评测完成 - 成功={}/{}", results.size(), availableModels.size());
        return results;
    }

    /**
     * 对指定模型列表执行评测。
     *
     * @param modelNames 模型名称列表
     * @param prompt     评测Prompt
     * @return 指定模型的评测结果列表
     */
    public List<ModelBenchmarkResp> runBenchmarksByModels(List<String> modelNames, String prompt) {
        log.info("[ModelBenchmarkService] 开始评测指定模型列表 - modelNames={}, prompt长度={}",
                modelNames, prompt != null ? prompt.length() : 0);

        List<ModelBenchmarkResp> results = new ArrayList<>();
        for (String modelName : modelNames) {
            if (modelName != null && !modelName.isBlank()) {
                ModelBenchmarkResp result = runBenchmark(modelName.trim(), prompt);
                results.add(result);
            }
        }

        log.info("[ModelBenchmarkService] 指定模型评测完成 - 成功={}/{}", results.size(), modelNames.size());
        return results;
    }

    /**
     * 获取历史评测记录（分页）。
     *
     * @param page     页码
     * @param pageSize 每页大小
     * @return 分页评测结果
     */
    public IPage<ModelBenchmarkResult> getResults(int page, int pageSize) {
        log.info("[ModelBenchmarkService] 查询评测结果 - page={}, pageSize={}", page, pageSize);

        Page<ModelBenchmarkResult> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<ModelBenchmarkResult> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(ModelBenchmarkResult::getBenchmarkTime);

        return benchmarkResultMapper.selectPage(pageParam, queryWrapper);
    }

    /**
     * 获取支持的模型列表及状态。
     *
     * @return 模型信息列表
     */
    public List<ModelInfo> getAvailableModels() {
        return llmChatService.getAvailableModels();
    }

    /**
     * 获取预设的Prompt模板列表。
     *
     * @return 模板列表
     */
    public List<PromptTemplateResp> getPromptTemplates() {
        log.info("[ModelBenchmarkService] 获取预设Prompt模板列表");
        return List.of(
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
    }

    /**
     * 根据模型名称解析Provider。
     */
    private String resolveProvider(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return LlmProviderType.DASHSCOPE.name();
        }
        String lower = modelName.toLowerCase();
        if (lower.startsWith("minimax") || lower.startsWith("abab")) {
            return LlmProviderType.MINIMAX.name();
        }
        return LlmProviderType.DASHSCOPE.name();
    }

    /**
     * 简单估算Token数量。
     * 实际应从响应中获取，这里做简化估算。
     */
    private int estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // 简单估算：中文按字符数，英文按空格分词，约1 token ≈ 2 字符
        return content.length() / 2;
    }
}
