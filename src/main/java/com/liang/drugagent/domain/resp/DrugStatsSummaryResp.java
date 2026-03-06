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

    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }

    public String getDateRange() { return dateRange; }
    public void setDateRange(String dateRange) { this.dateRange = dateRange; }

    public Integer getTotalDays() { return totalDays; }
    public void setTotalDays(Integer totalDays) { this.totalDays = totalDays; }

    public Double getDailyAvg() { return dailyAvg; }
    public void setDailyAvg(Double dailyAvg) { this.dailyAvg = dailyAvg; }

    public Double getDailyMax() { return dailyMax; }
    public void setDailyMax(Double dailyMax) { this.dailyMax = dailyMax; }

    public Double getDailyMin() { return dailyMin; }
    public void setDailyMin(Double dailyMin) { this.dailyMin = dailyMin; }

    public Double getStdDev() { return stdDev; }
    public void setStdDev(Double stdDev) { this.stdDev = stdDev; }

    public Double getGrowthRate() { return growthRate; }
    public void setGrowthRate(Double growthRate) { this.growthRate = growthRate; }

    public List<AnomalyPointResp> getAnomalies() { return anomalies; }
    public void setAnomalies(List<AnomalyPointResp> anomalies) { this.anomalies = anomalies; }

    public List<DailyUsageResp> getDailyDetails() { return dailyDetails; }
    public void setDailyDetails(List<DailyUsageResp> dailyDetails) { this.dailyDetails = dailyDetails; }
}
