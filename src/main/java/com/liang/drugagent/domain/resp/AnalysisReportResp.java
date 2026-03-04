package com.liang.drugagent.domain.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 药品分析报告
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReportResp {
    private String trendSummary;     // 趋势总结
    private String anomalyAnalysis;  // 异常分析
    private String riskLevel;        // 风险等级 (LOW/MEDIUM/HIGH/CRITICAL)
    private String riskReason;       // 风险等级判断依据
    private List<String> suggestions; // 监管建议
    private DrugStatsSummaryResp stats; // 统计数据 (用于前端图表展示)
}
