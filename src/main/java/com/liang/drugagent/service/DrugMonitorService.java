package com.liang.drugagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.domain.req.DrugAnalyzeReq;
import com.liang.drugagent.prompt.DrugAnalysisPrompt;
import com.liang.drugagent.domain.resp.AnalysisReportResp;
import com.liang.drugagent.domain.resp.DrugStatsSummaryResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

/**
 * 药品数据监控分析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugMonitorService {

    private final QwenService qwenService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 这里如果引入了 Mapper 需要替换为实际调用，目前先 mock 后续逻辑对接
    // private final DrugUsageRecordMapper drugUsageRecordMapper;

    /**
     * AI 智能分析药品数据
     *
     * @param dto 分析请求参数
     * @return 结构化分析报告
     */
    public AnalysisReportResp analyzeDrug(DrugAnalyzeReq req) {
        // 1. 聚合统计 (这部分后续对接真实数据库，目前先提供一个测试数据组装)
        DrugStatsSummaryResp stats = aggregateStatsMock(req.getDrugName(), req.getStartDate(), req.getEndDate());
        
        // 2. 构建 Prompt
        String systemPrompt = DrugAnalysisPrompt.SYSTEM_PROMPT;
        String userPrompt = DrugAnalysisPrompt.buildUserPrompt(stats);
        
        // 3. 调用千问
        log.info("开始调用千问进行药品分析: {}", req.getDrugName());
        String aiResponse = qwenService.chatWithSystem(systemPrompt, userPrompt);
        log.info("千问分析结果为: \n{}", aiResponse);
        
        // 4. 解析 JSON 响应
        AnalysisReportResp report;
        try {
            // 清理可能包含的 markdown json 标记
            String cleanJson = aiResponse.replaceAll("```json\\n", "").replaceAll("```", "").trim();
            report = objectMapper.readValue(cleanJson, AnalysisReportResp.class);
        } catch (JsonProcessingException e) {
            log.error("JSON解析失败，降级处理返回纯文本", e);
            report = new AnalysisReportResp();
            report.setTrendSummary(aiResponse);
            report.setRiskLevel("UNKNOWN");
            report.setRiskReason("JSON解析失败");
            report.setSuggestions(new ArrayList<>());
        }
        
        // 5. 补充统计数据到报告（前端图表需要）
        report.setStats(stats);
        return report;
    }
    
    /**
     * Mock 统计数据（后续可替换为查库聚合）
     */
    private DrugStatsSummaryResp aggregateStatsMock(String drugName, LocalDate startDate, LocalDate endDate) {
        return DrugStatsSummaryResp.builder()
                .drugName(drugName)
                .dateRange(startDate + " ~ " + endDate)
                .totalDays(30)
                .dailyAvg(150.3)
                .dailyMax(380.0)
                .dailyMin(45.0)
                .stdDev(52.7)
                .growthRate(12.5)
                .anomalies(new ArrayList<>())
                .dailyDetails(new ArrayList<>())
                .build();
    }
}
