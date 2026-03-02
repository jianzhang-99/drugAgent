package com.liang.drugagent.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 药品分析请求 DTO
 */
@Data
public class DrugAnalyzeDTO {
    /**
     * 药品名称
     */
    private String drugName;
    
    /**
     * 分析开始日期
     */
    private LocalDate startDate;
    
    /**
     * 分析结束日期
     */
    private LocalDate endDate;
}
