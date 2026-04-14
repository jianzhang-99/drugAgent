package com.liang.drugagent.benchmark.resp;

import com.liang.drugagent.benchmark.entity.ManualReviewSample;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 抽样响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleSelectionResp {

    /**
     * 本次抽出的样本列表
     */
    private List<ManualReviewSample> samples;

    /**
     * 实际抽取数量
     */
    private int selectedCount;

    /**
     * 可用候选数量
     */
    private int availableCount;

    /**
     * 抽样策略说明
     */
    private String strategyDescription;
}
