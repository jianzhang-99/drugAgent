package com.liang.drugagent.tenderreview.domain;

import com.liang.drugagent.tenderreview.domain.enums.BlockType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Block {

    /** 块唯一 ID */
    private String blockId;

    /** 所属文档 ID */
    private String documentId;

    /** 块类型 */
    private BlockType blockType;

    /** 所属章节路径 */
    private String chapterPath;

    /** 规范化后的文本内容 */
    private String content;

    /** 原始文本（含原始空白） */
    private String rawContent;

    /** 位置锚点 */
    private Anchor anchor;

    /** 特征标签列表（如 PHONE_FIELD、PRICE_FIELD 等） */
    private List<String> featureTags;
}
