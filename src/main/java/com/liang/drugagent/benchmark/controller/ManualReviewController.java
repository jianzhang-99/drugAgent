package com.liang.drugagent.benchmark.controller;

import com.liang.drugagent.benchmark.entity.ManualReviewSample;
import com.liang.drugagent.benchmark.req.DeviationAnalysisReq;
import com.liang.drugagent.benchmark.req.SampleSelectionReq;
import com.liang.drugagent.benchmark.resp.DeviationAnalysisResp;
import com.liang.drugagent.benchmark.resp.DeviationReportResp;
import com.liang.drugagent.benchmark.resp.SampleSelectionResp;
import com.liang.drugagent.benchmark.service.DeviationAnalysisService;
import com.liang.drugagent.benchmark.service.SampleSelectionService;
import com.liang.drugagent.benchmark.service.DeviationAnalysisService.DeviationReport;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工抽检控制器。
 *
 * <p>提供抽样策略执行、偏差分析、报告生成等接口，
 * 供评测人员使用以评估和改进标书审查系统的准确性。
 */
@Slf4j
@RestController
@RequestMapping("/api/benchmark/manual-review")
public class ManualReviewController {

    private final SampleSelectionService sampleSelectionService;
    private final DeviationAnalysisService deviationAnalysisService;

    public ManualReviewController(SampleSelectionService sampleSelectionService,
                                  DeviationAnalysisService deviationAnalysisService) {
        this.sampleSelectionService = sampleSelectionService;
        this.deviationAnalysisService = deviationAnalysisService;
    }

    /**
     * 执行抽样。
     *
     * @param req 抽样请求（策略和数量）
     * @return 抽出的样本列表
     */
    @PostMapping("/sample")
    public ResponseEntity<SampleSelectionResp> selectSamples(@Valid @RequestBody SampleSelectionReq req) {
        log.info("[人工抽检] 执行抽样请求: 策略={}, 数量={}", req.getStrategy(), req.getSampleSize());

        List<ManualReviewSample> samples = sampleSelectionService.selectSamples(
                req.getStrategy(), req.getSampleSize());

        SampleSelectionResp resp = SampleSelectionResp.builder()
                .samples(samples)
                .selectedCount(samples.size())
                .strategyDescription(getStrategyDescription(req.getStrategy()))
                .build();

        return ResponseEntity.ok(resp);
    }

    /**
     * 查询待审核样本列表。
     */
    @GetMapping("/samples/pending")
    public ResponseEntity<List<ManualReviewSample>> getPendingSamples() {
        log.info("[人工抽检] 查询待审核样本");
        List<ManualReviewSample> samples = sampleSelectionService.findPendingSamples();
        return ResponseEntity.ok(samples);
    }

    /**
     * 根据样本ID查询样本详情。
     */
    @GetMapping("/samples/{sampleId}")
    public ResponseEntity<ManualReviewSample> getSample(@PathVariable String sampleId) {
        log.info("[人工抽检] 查询样本详情: sampleId={}", sampleId);
        return sampleSelectionService.findBySampleId(sampleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 提交偏差分析结果。
     *
     * @param req 偏差分析请求（包含人工判定结果）
     * @return 偏差记录列表
     */
    @PostMapping("/deviation/analyze")
    public ResponseEntity<DeviationAnalysisResp> analyzeDeviation(@Valid @RequestBody DeviationAnalysisReq req) {
        log.info("[人工抽检] 提交偏差分析: sampleId={}, 审核人={}", req.getSampleId(), req.getReviewedBy());

        var deviations = deviationAnalysisService.analyzeDeviation(
                req.getSampleId(),
                req.getManualResult(),
                req.getManualComment());

        DeviationAnalysisResp resp = DeviationAnalysisResp.builder()
                .deviationRecords(deviations)
                .deviationCount(deviations.size())
                .analyzedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();

        return ResponseEntity.ok(resp);
    }

    /**
     * 获取偏差报告。
     *
     * @return 包含规则precision/recall/F1和系统性偏差模式的完整报告
     */
    @GetMapping("/deviation/report")
    public ResponseEntity<DeviationReportResp> getDeviationReport() {
        log.info("[人工抽检] 生成偏差报告");

        DeviationReport report = deviationAnalysisService.generateDeviationReport();

        DeviationReportResp resp = DeviationReportResp.builder()
                .totalSamples(report.getTotalSamples())
                .completedSamples(report.getCompletedSamples())
                .totalDeviations(report.getTotalDeviations())
                .ruleMetrics(report.getRuleMetrics())
                .deviationPatterns(report.getDeviationPatterns())
                .generatedAt(report.getGeneratedAt().toString())
                .build();

        return ResponseEntity.ok(resp);
    }

    /**
     * 获取规则性能指标。
     */
    @GetMapping("/metrics/rule")
    public ResponseEntity<List<DeviationAnalysisService.RuleMetrics>> getRuleMetrics() {
        log.info("[人工抽检] 获取规则性能指标");
        List<DeviationAnalysisService.RuleMetrics> metrics = deviationAnalysisService.calculateRuleMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * 获取系统性偏差模式。
     */
    @GetMapping("/patterns")
    public ResponseEntity<List<DeviationAnalysisService.DeviationPattern>> getDeviationPatterns() {
        log.info("[人工抽检] 获取系统性偏差模式");
        List<DeviationAnalysisService.DeviationPattern> patterns = deviationAnalysisService.identifySystematicPatterns();
        return ResponseEntity.ok(patterns);
    }

    private String getStrategyDescription(String strategy) {
        return switch (strategy) {
            case "RANDOM" -> "随机抽样：从历史case中均匀随机选取，不偏向任何特定类型";
            case "HIGH_DEVIATION" -> "高偏差优先：选择LLM高置信度但评分处于边界的case，识别可能的误判";
            case "HIGH_RISK_BOUNDARY" -> "高风险边界：选择评分靠前但未达阈值的case，识别可能的漏报";
            default -> "未知策略";
        };
    }
}
