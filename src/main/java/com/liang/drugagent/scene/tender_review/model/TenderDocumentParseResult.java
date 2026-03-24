package com.liang.drugagent.scene.tender_review.model;

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

    /** 结构化数据 schema 版本。 */
    private String schemaVersion;
    /** 解析器版本。 */
    private String parserVersion;
    /** 本次解析是否成功。 */
    private Boolean parseSuccess;

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
