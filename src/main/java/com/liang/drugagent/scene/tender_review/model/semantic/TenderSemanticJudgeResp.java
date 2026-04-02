package com.liang.drugagent.scene.tender_review.model.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 语义裁决响应对象。
 * 封装 LLM 返回的结构化判断结果。
 *
 * @author architect
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSemanticJudgeResp {

    /**
     * 是否命中规则。
     */
    private Boolean hit;

    /**
     * 规则编码。
     */
    private String ruleCode;

    /**
     * 风险类型，如 "collusion"（串标）。
     */
    private String riskType;

    /**
     * 置信度，范围 0.0 ~ 1.0。
     */
    private Double confidence;

    /**
     * 建议权重。
     */
    private Integer suggestedWeight;

    /**
     * 简短结论。
     */
    private String conclusion;

    /**
     * 判断理由。
     */
    private String reason;

    /**
     * 关键证据片段列表。
     */
    private List<TenderSemanticEvidence> evidences;

    /**
     * 保留意见或注意事项。
     */
    private List<String> cautionNotes;
}
