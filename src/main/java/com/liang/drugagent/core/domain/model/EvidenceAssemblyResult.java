package com.liang.drugagent.core.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 证据组装结果。
 *
 * <p>封装证据组装流程的最终结果，包含：
 * <ul>
 *   <li>扁平化的证据项列表（flatItems）</li>
 *   <li>按业务维度分组的证据组列表（groups）</li>
 * </ul>
 *
 * @author drug-agent
 */
@Getter
@Setter
public class EvidenceAssemblyResult {

    /** 扁平化的证据项列表（去重、排序后） */
    private List<EvidenceItem> flatItems = new ArrayList<>();

    /** 按业务维度分组的证据组列表 */
    private List<EvidenceGroup> groups = new ArrayList<>();
}
