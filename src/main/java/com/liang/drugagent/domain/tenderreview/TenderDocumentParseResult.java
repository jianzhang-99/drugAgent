package com.liang.drugagent.domain.tenderreview;

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
public class TenderDocumentParseResult {

    /** 对应的文档 ID */
    private String docId;

    /** 章节树（顶层节点列表） */
    private List<TenderSectionNode> sectionTree;

    /** 段落块列表（blockType = PARAGRAPH） */
    private List<Block> paragraphBlocks;

    /** 表格块列表（blockType = TABLE） */
    private List<Block> tableBlocks;

    /** 结构化字段列表（从段落/表格中提取） */
    private List<Field> fields;

    /** 解析元信息 */
    private ExtractionMeta extractionMeta;

    public int getParagraphCount() {
        return paragraphBlocks == null ? 0 : paragraphBlocks.size();
    }

    public int getTableCount() {
        return tableBlocks == null ? 0 : tableBlocks.size();
    }

    public int getFieldCount() {
        return fields == null ? 0 : fields.size();
    }
}
