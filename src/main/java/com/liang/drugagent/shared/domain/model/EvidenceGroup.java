package com.liang.drugagent.shared.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 证据分组。
 *
 * <p>用于将多条证据项（EvidenceItem）按业务含义聚合为一个证据组，
 * 方便前端展示和报告生成。</p>
 *
 * @author drug-agent
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceGroup {

    /** 分组唯一标识Key */
    private String groupKey;

    /** 分组标题 */
    private String title;

    /** 分组摘要描述 */
    private String summary;

    /** 证据来源（如"规则引擎"、"AI分析"） */
    private String source;

    /** 该分组下的证据项列表 */
    private List<EvidenceItem> items = new ArrayList<>();
}
