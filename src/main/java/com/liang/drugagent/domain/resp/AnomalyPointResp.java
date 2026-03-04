package com.liang.drugagent.domain.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 异常点信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyPointResp {
    private LocalDate date;
    private BigDecimal amount;
    private String type; // 偏高/偏低
}
