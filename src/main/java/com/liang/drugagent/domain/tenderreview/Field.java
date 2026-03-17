package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

/**
 * 结构化字段。
 *
 * @author liangjiajian
 */
@Getter
@Setter
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
    /** 字段定位锚点。 */
    private Anchor anchor;
    /** 字段抽取置信度。 */
    private Double confidence;
}
