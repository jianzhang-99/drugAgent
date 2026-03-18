package com.liang.drugagent.domain.tenderreview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSectionNode {

    /** 章节标题文本 */
    private String title;

    /** 章节层级（1 = 顶层） */
    private int level;

    /** 子章节列表 */
    @Builder.Default
    private List<TenderSectionNode> children = new ArrayList<>();
}
