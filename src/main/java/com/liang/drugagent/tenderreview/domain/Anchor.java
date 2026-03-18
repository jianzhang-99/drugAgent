package com.liang.drugagent.tenderreview.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Anchor {

    /** 当前段落/表格所属的章节路径（以章节标题首字符标识） */
    private String chapterPath;

    /** 段落在文档中的顺序索引（从 0 开始），表格块为 -1 */
    private int paragraphIndex;

    /** 表格在文档中的顺序索引（从 0 开始），段落块为 -1 */
    private int tableIndex;

    // ---- 下游对接文档要求字段 ----

    /** 页码（1-based，暂时留 null，后续可扩展） */
    private Integer pageNo;

    /** 章节编号，例如 "3.2" */
    private String sectionNo;

    /** 段落序号（同 paragraphIndex，1-based 展示用） */
    private Integer paragraphNo;

    /** 表格序号（同 tableIndex，1-based 展示用） */
    private Integer tableNo;
}
