package com.liang.drugagent.scene.tender_review.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险融合结果。
 *
 * <p>标书审查中多条规则命中后，经风险融合算法计算得出的综合风险评估结果。</p>
 *
 * @author drug-agent
 */
@Getter
@Setter
public class RiskFusionResult {

    /** 综合风险等级（如"HIGH"、"MEDIUM"、"LOW"） */
    private String riskLevel;

    /** 风险评分（0-100） */
    private Integer score;

    /** 风险摘要描述 */
    private String summary;

    /** 参与融合的原因码列表 */
    private List<String> reasonCodes = new ArrayList<>();
}
