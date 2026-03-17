package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

/**
 * 原文内容块。
 *
 * @author liangjiajian
 */
@Getter
@Setter
public class Block {

    /** 内容块唯一 ID。 */
    private String blockId;
    /** 所属文档 ID。 */
    private String documentId;
    /** 内容块类型，例如 paragraph、table。 */
    private String blockType;
    /** 章节路径。 */
    private String chapterPath;
    /** 规范化后的内容。 */
    private String content;
    /** 保留原始格式的内容。 */
    private String rawContent;
    /** 原文定位锚点。 */
    private Anchor anchor;
}
