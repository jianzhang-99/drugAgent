package com.liang.drugagent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 每日用量信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyUsageVO {
    private LocalDate usageDate;
    private BigDecimal dailyTotal;
    private Integer recordCount;
}
