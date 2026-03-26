package com.liang.drugagent.scene.tender_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则命中的证据引用。
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    // === Inline Anchor fields ===
    /** 段落在文档中的顺序索引，表格块为 -1。 */
    private Integer anchorParagraphIndex;
    /** 表格在文档中的顺序索引，段落块为 -1。 */
    private Integer anchorTableIndex;
    /** 页码。 */
    private Integer anchorPageNo;
    /** 章节编号，例如 3.2。 */
    private String anchorSectionNo;
    /** 段落序号。 */
    private Integer anchorParagraphNo;
    /** 表格序号。 */
    private Integer anchorTableNo;
}
