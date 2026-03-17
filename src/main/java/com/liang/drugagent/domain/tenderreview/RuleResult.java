package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 围标规则引擎输出结果。
 *
 * @author liangjiajian
 */
@Getter
@Setter
public class RuleResult {

    /** 命中结果列表。 */
    private List<RuleHit> hits = new ArrayList<>();
}
