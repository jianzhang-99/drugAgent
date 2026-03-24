package com.liang.drugagent.scene.tender_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 标书章节节点。
 *
 * <p>表示标书文档的树形结构章节，用于：
 * <ul>
 *   <li>解析标书的目录结构</li>
 *   <li>按章节维度组织审查结果</li>
 *   <li>支持章节级别的免责判定</li>
 * </ul>
 *
 * @author drug-agent
 */
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
