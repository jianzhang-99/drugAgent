package com.liang.drugagent.vo;

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
public class DrugStatsSummaryVO {
    private String drugName;
    private String dateRange;
    private Integer totalDays;
    private Double dailyAvg;
    private Double dailyMax;
    private Double dailyMin;
    private Double stdDev;
    private Double growthRate;
    private List<AnomalyPointVO> anomalies;
    private List<DailyUsageVO> dailyDetails;
}
