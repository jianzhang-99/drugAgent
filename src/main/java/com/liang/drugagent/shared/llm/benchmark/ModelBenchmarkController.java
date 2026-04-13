package com.liang.drugagent.shared.llm.benchmark;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.liang.drugagent.shared.llm.ModelInfo;
import com.liang.drugagent.shared.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型评测控制器。
 *
 * <p>提供模型评测的REST API接口：
 * <ul>
 *   <li>单模型评测</li>
 *   <li>批量评测所有可用模型</li>
 *   <li>查询历史评测记录</li>
 *   <li>获取支持的模型列表</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Slf4j
@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
@Tag(name = "ModelBenchmark", description = "模型评测接口")
public class ModelBenchmarkController {

    private final ModelBenchmarkService benchmarkService;

    /**
     * 执行单模型评测。
     *
     * @param modelName 模型名称
     * @param prompt    评测使用的Prompt
     * @return 评测响应
     */
    @Operation(summary = "执行单模型评测")
    @PostMapping("/run")
    public Result<ModelBenchmarkResp> runBenchmark(@RequestParam String modelName, @RequestParam String prompt) {
        log.info("[ModelBenchmarkController] 收到单模型评测请求 - modelName={}, prompt长度={}",
                modelName, prompt != null ? prompt.length() : 0);
        ModelBenchmarkResp result = benchmarkService.runBenchmark(modelName, prompt);
        return Result.success(result);
    }

    /**
     * 执行单模型评测或批量评测。
     * 当 modelName 不为空时评测指定模型，为空时评测所有可用模型。
     *
     * @param req 评测请求（包含 modelName 和 prompt）
     * @return 评测结果列表
     */
    @Operation(summary = "执行模型评测（单模型或批量）")
    @PostMapping("/run-all")
    public Result<List<ModelBenchmarkResp>> runAllBenchmarks(@RequestBody ModelBenchmarkRunReq req) {
        log.info("[ModelBenchmarkController] 收到评测请求 - modelName={}, prompt长度={}",
                req.getModelName(), req.getPrompt() != null ? req.getPrompt().length() : 0);
        List<ModelBenchmarkResp> results;
        if (req.getModelName() != null && !req.getModelName().isBlank()) {
            results = List.of(benchmarkService.runBenchmark(req.getModelName(), req.getPrompt()));
        } else {
            results = benchmarkService.runAllBenchmarks(req.getPrompt());
        }
        return Result.success(results);
    }

    /**
     * 获取历史评测记录（分页）。
     *
     * @param page     页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页的历史记录
     */
    @Operation(summary = "获取历史评测记录")
    @GetMapping("/results")
    public Result<IPage<ModelBenchmarkResult>> getResults(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("[ModelBenchmarkController] 查询评测结果 - page={}, pageSize={}", page, pageSize);
        IPage<ModelBenchmarkResult> results = benchmarkService.getResults(page, pageSize);
        return Result.success(results);
    }

    /**
     * 获取支持的模型列表及状态。
     *
     * @return 模型信息列表
     */
    @Operation(summary = "获取支持的模型列表及状态")
    @GetMapping("/models")
    public Result<List<ModelInfo>> getAvailableModels() {
        log.info("[ModelBenchmarkController] 获取可用模型列表");
        List<ModelInfo> models = benchmarkService.getAvailableModels();
        return Result.success(models);
    }
}
