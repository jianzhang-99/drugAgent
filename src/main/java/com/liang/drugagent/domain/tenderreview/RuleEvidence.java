package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

/**
 * 规则命中的证据引用。
 *
 * @author liangjiajian
 */
@Getter
@Setter
public class RuleEvidence {

    /** 证据所属文档 ID。 */
    private String documentId;
    /** 关联字段 ID。 */
    private String fieldId;
    /** 关联内容块 ID。 */
    private String blockId;
    /** 本条证据命中的值。 */
    private String matchedValue;
    /** 证据所在章节路径。 */
    private String chapterPath;
    /** 证据定位锚点。 */
    private Anchor anchor;
}
