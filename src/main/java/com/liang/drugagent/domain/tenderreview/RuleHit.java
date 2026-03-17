package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条围标规则命中结果。
 *
 * @author liangjiajian
 */
@Getter
@Setter
public class RuleHit {

    /** 命中记录唯一 ID。 */
    private String hitId;
    /** 规则编码。 */
    private String ruleCode;
    /** 规则名称。 */
    private String ruleName;
    /** 所属比对范围 ID。 */
    private String scopeId;
    /** 风险类型，例如 collusion。 */
    private String riskType;
    /** 优先级，例如 HIGH、MEDIUM。 */
    private String priority;
    /** 风险权重。 */
    private Integer weight;
    /** 命中的标准化值。 */
    private String matchedValue;
    /** 面向展示的触发摘要。 */
    private String triggerSummary;
    /** 关联文档 ID 列表。 */
    private List<String> documentIds = new ArrayList<>();
    /** 关联字段 ID 列表。 */
    private List<String> fieldIds = new ArrayList<>();
    /** 关联内容块 ID 列表。 */
    private List<String> blockIds = new ArrayList<>();
    /** 证据明细。 */
    private List<RuleEvidence> evidences = new ArrayList<>();
    /** 规则版本号。 */
    private String version;
    private Integer originalWeight;
    private Integer adjustedWeight;
    private Boolean exempted;
    private String exemptionReason;
}
