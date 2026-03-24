package com.liang.drugagent.tool.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档章节节点。
 *
 * @author liangjiajian
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSectionNode {

    /** 章节标题文本 */
    private String title;
    /** 章节层级（1 = 顶层） */
    private int level;
    /** 子章节列表 */
    @Builder.Default
    private List<DocumentSectionNode> children = new ArrayList<>();
}
