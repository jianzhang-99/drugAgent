package com.liang.drugagent.prompt;

import com.liang.drugagent.domain.resp.AnomalyPointResp;
import com.liang.drugagent.domain.resp.DrugStatsSummaryResp;

/**
 * 药品分析 Prompt 模板
 */
public class DrugAnalysisPrompt {
    
    public static final String SYSTEM_PROMPT = """
        你是一位资深的医院药品监管分析专家。你的任务是基于提供的药品使用统计数据，
        给出专业的分析报告。
        
        请严格按以下JSON格式返回（不要返回其他内容）：
        {
          "trendSummary": "趋势总结（2-3句话）",
          "anomalyAnalysis": "异常分析（如有异常点，推测可能原因）",
          "riskLevel": "LOW/MEDIUM/HIGH/CRITICAL",
          "riskReason": "风险等级判断依据",
          "suggestions": ["建议1", "建议2", "建议3"]
        }
        """;
    
    public static String buildUserPrompt(DrugStatsSummaryResp stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 药品使用数据分析请求\n\n");
        sb.append("**药品名称**：").append(stats.getDrugName()).append("\n");
        sb.append("**分析时段**：").append(stats.getDateRange()).append("\n");
        sb.append("**统计天数**：").append(stats.getTotalDays()).append("天\n\n");
        
        sb.append("### 统计摘要\n");
        sb.append("- 日均用量：").append(String.format("%.1f", stats.getDailyAvg())).append("\n");
        sb.append("- 最大单日用量：").append(String.format("%.1f", stats.getDailyMax())).append("\n");
        sb.append("- 最小单日用量：").append(String.format("%.1f", stats.getDailyMin())).append("\n");
        sb.append("- 标准差：").append(String.format("%.2f", stats.getStdDev())).append("\n");
        sb.append("- 环比增长率：").append(String.format("%.1f%%", stats.getGrowthRate())).append("\n\n");
        
        if (stats.getAnomalies() != null && !stats.getAnomalies().isEmpty()) {
            sb.append("### 已检测到的异常点\n");
            for (AnomalyPointResp a : stats.getAnomalies()) {
                sb.append("- ").append(a.getDate()).append("：用量 ")
                  .append(a.getAmount()).append("，").append(a.getType()).append("\n");
            }
        } else {
            sb.append("### 已检测到的异常点\n- 暂无异常点\n");
        }
        
        sb.append("\n请基于以上数据进行专业分析，给出趋势判断、异常原因推测、风险等级和监管建议。");
        return sb.toString();
    }
}
