package com.liang.drugagent.tool.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 通用文档内容块。
 *
 * @author liangjiajian
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentBlock {

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
    /** 特征标签列表。 */
    private List<String> featureTags;

    // === 定位信息 ===
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
