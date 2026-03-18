package com.liang.drugagent.domain.tenderreview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 原文定位锚点。
 *
 * @author liangjiajian
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Anchor {

    /** 当前段落/表格所属的章节路径。 */
    private String chapterPath;
    /** 段落在文档中的顺序索引，表格块为 -1。 */
    private Integer paragraphIndex;
    /** 表格在文档中的顺序索引，段落块为 -1。 */
    private Integer tableIndex;

    /** 页码。 */
    private Integer pageNo;
    /** 章节编号，例如 3.2。 */
    private String sectionNo;
    /** 段落序号。 */
    private Integer paragraphNo;
    /** 表格序号。 */
    private Integer tableNo;
}
