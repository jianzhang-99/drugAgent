package com.liang.drugagent.benchmark.req;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 偏差分析请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviationAnalysisReq {

    /**
     * 样本ID
     */
    @NotBlank(message = "样本ID不能为空")
    private String sampleId;

    /**
     * 人工判定结果（JSON格式的Map）
     */
    private Map<String, Object> manualResult;

    /**
     * 人工审核意见
     */
    private String manualComment;

    /**
     * 审核人
     */
    private String reviewedBy;
}
