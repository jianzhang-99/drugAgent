package com.liang.drugagent.domain.req;

import lombok.Data;

import java.time.LocalDate;

/**
 * 药品分析请求 Req
 */
@Data
public class DrugAnalyzeReq {
    private String drugName;
    private LocalDate startDate;
    private LocalDate endDate;

    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
