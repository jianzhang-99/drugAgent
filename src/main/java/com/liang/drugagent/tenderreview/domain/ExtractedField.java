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
public class ExtractedField {

    /** 字段唯一 ID */
    private String fieldId;

    /** 所属文档 ID */
    private String documentId;

    /** 来源内容块 ID */
    private String blockId;

    /** 字段类型，例如 contact_phone、contact_email、bid_price、team_member */
    private String fieldType;

    /** 字段名称（展示用），例如 "联系电话"、"投标报价" */
    private String fieldName;

    /** 字段原始值（从原文提取的文本） */
    private String fieldValue;

    /** 规范化后的值（用于比对） */
    private String normalizedValue;

    /** 规范化键（用于去重和比对），例如 "phone:13812345678" */
    private String normalizedKey;

    /** 所属章节路径 */
    private String chapterPath;

    /** 位置锚点 */
    private Anchor anchor;

    /** 提取置信度（0.0 ~ 1.0） */
    private Double confidence;
}
