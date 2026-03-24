package com.liang.drugagent.scene.tender_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 结构化字段。
 *
 * @author liangjiajian
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Field {

    /** 字段唯一 ID。 */
    private String fieldId;
    /** 所属文档 ID。 */
    private String documentId;
    /** 来源内容块 ID。 */
    private String blockId;
    /** 字段类型，例如 contact_phone、team_member。 */
    private String fieldType;
    /** 字段展示名称。 */
    private String fieldName;
    /** 原始字段值。 */
    private String fieldValue;
    /** 标准化后的字段值。 */
    private String normalizedValue;
    /** 用于聚合比较的归一化键。 */
    private String normalizedKey;
    /** 字段所在章节路径。 */
    private String chapterPath;
    /** 字段抽取置信度。 */
    private Double confidence;

    // === Inline Anchor fields ===
    /** 当前段落/表格所属的章节路径。 */
    private String anchorChapterPath;
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
