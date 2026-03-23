package com.liang.drugagent.scenes.tender_review.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 免责判定结果。
 *
 * <p>封装规则命中的免责判定结果，包含：
 * <ul>
 *   <li>effectiveHits：有效命中（未被免责的规则命中）</li>
 *   <li>exemptionHits：免责命中（触发免责条件的规则命中）</li>
 * </ul>
 *
 * @author drug-agent
 */
@Getter
@Setter
public class ExemptionResult {

    /** 有效命中列表（未被免责的规则命中） */
    private List<RuleHit> effectiveHits = new ArrayList<>();

    /** 免责命中列表（触发免责条件的规则命中） */
    private List<ExemptionHit> exemptionHits = new ArrayList<>();
}
