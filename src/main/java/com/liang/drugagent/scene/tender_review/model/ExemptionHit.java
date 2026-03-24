package com.liang.drugagent.scene.tender_review.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 免责命中记录。
 *
 * <p>记录触发免责条件的规则命中详情，用于追踪哪些风险被免责处理。</p>
 *
 * @author drug-agent
 */
@Getter
@Setter
public class ExemptionHit {

    /** 命中记录唯一ID */
    private String hitId;

    /** 规则代码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 免责类型（如"REFERENCE_TEMPLATE"、"LOW_RISK_CHAPTER"） */
    private String exemptionType;

    /** 采取的行动（如"DOWNGRADE"、"IGNORE"） */
    private String action;

    /** 免责原因描述 */
    private String reason;

    /** 免责前的风险权重 */
    private Integer beforeWeight;

    /** 免责后的风险权重 */
    private Integer afterWeight;
}
