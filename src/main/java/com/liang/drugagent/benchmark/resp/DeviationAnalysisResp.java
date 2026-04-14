package com.liang.drugagent.benchmark.resp;

import com.liang.drugagent.benchmark.entity.EvaluationDeviationRecord;
import com.liang.drugagent.benchmark.service.DeviationAnalysisService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 偏差分析响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviationAnalysisResp {

    /**
     * 偏差记录列表
     */
    private List<EvaluationDeviationRecord> deviationRecords;

    /**
     * 偏差数量
     */
    private int deviationCount;

    /**
     * 分析时间
     */
    private String analyzedAt;
}
