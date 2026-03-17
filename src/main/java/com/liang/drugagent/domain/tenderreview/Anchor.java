package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

/**
 * 原文定位锚点。
 *
 * @author liangjiajian
 */
@Getter
@Setter
public class Anchor {

    /** 页码。 */
    private Integer pageNo;
    /** 章节编号，例如 3.2。 */
    private String sectionNo;
    /** 段落序号。 */
    private Integer paragraphNo;
    /** 表格序号。 */
    private Integer tableNo;
}
