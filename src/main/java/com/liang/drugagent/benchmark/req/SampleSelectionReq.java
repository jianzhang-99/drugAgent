package com.liang.drugagent.benchmark.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 抽样请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleSelectionReq {

    /**
     * 抽样策略：RANDOM / HIGH_DEVIATION / HIGH_RISK_BOUNDARY
     */
    @NotBlank(message = "抽样策略不能为空")
    private String strategy;

    /**
     * 抽样数量
     */
    @Min(value = 1, message = "抽样数量最小为1")
    @Max(value = 100, message = "抽样数量最大为100")
    private int sampleSize = 10;
}
