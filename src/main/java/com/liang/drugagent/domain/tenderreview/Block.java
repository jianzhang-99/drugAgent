package com.liang.drugagent.domain.tenderreview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 原文内容块。
 *
 * @author liangjiajian
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    /** 规则与解析使用的特征标签。 */
    private List<String> featureTags;
}
