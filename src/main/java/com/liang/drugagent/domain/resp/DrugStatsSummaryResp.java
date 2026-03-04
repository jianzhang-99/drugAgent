package com.liang.drugagent.domain.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 药品数据统计摘要
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugStatsSummaryResp {
    private String drugName;
    private String dateRange;
    private Integer totalDays;
    private Double dailyAvg;
    private Double dailyMax;
    private Double dailyMin;
    private Double stdDev;
    private Double growthRate;
    private List<AnomalyPointResp> anomalies;
    private List<DailyUsageResp> dailyDetails;
}
