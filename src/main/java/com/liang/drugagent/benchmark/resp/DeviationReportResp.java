package com.liang.drugagent.benchmark.resp;

import com.liang.drugagent.benchmark.service.DeviationAnalysisService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 偏差报告响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviationReportResp {

    /**
     * 样本总数
     */
    private int totalSamples;

    /**
     * 已完成审核数
     */
    private int completedSamples;

    /**
     * 偏差记录总数
     */
    private int totalDeviations;

    /**
     * 各规则性能指标
     */
    private List<DeviationAnalysisService.RuleMetrics> ruleMetrics;

    /**
     * 系统性偏差模式
     */
    private List<DeviationAnalysisService.DeviationPattern> deviationPatterns;

    /**
     * 报告生成时间
     */
    private String generatedAt;
}
