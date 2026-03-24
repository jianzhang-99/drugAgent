package com.liang.drugagent.scene.tender_review.support.parser;

import com.liang.drugagent.scene.tender_review.model.Block;
import com.liang.drugagent.scene.tender_review.model.TenderDocumentParseResult;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** DOCX 格式解析器。 */
@Component
public class TenderDocxParser {
    /** 沿用 TenderTextStructureSupport 的 schema 版本，供外部（含测试）引用。 */
    public static final String SCHEMA_VERSION = TenderTextStructureSupport.SCHEMA_VERSION;
    /** 解析器版本。 */
    public static final String PARSER_VERSION = TenderTextStructureSupport.PARSER_VERSION;

    private final TenderTextStructureSupport textStructureSupport;

    public TenderDocxParser(TenderTextStructureSupport textStructureSupport) {
        this.textStructureSupport = textStructureSupport;
    }

    // ---- delegate helpers for backward-compat ----

    /** 委托文本规范化。 */
    public String normalizeText(String raw) { return textStructureSupport.normalizeText(raw); }

    /** 委托章节标题判断。 */
    public boolean isSectionHeader(String content) { return textStructureSupport.isSectionHeader(content); }

    /** 委托字段标签检测。 */
    public List<String> detectFieldTags(String content) { return textStructureSupport.detectFieldTags(content); }

    /**
     * 解析 docx 输入流，提取段落块、表格块、章节树和结构化字段。
     *
     * @param inputStream docx 文件流
     * @param docId       对应的文档 ID
     * @return 解析结果
     * @throws IOException 读取失败时抛出
     */
    public TenderDocumentParseResult parse(InputStream inputStream, String docId) throws IOException {
        boolean parseSuccess = false;
        List<Block> paragraphBlocks = new ArrayList<>();
        List<Block> tableBlocks = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.TenderSectionNode> sectionTree = new ArrayList<>();
        List<com.liang.drugagent.scene.tender_review.model.Field> fields = new ArrayList<>();

        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            String[] currentChapter = {""};
            int[] paragraphIndex = {0};
            int[] tableIndex = {0};

            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    Block block = processParagraph(p, docId, currentChapter, paragraphIndex, sectionTree);
                    paragraphBlocks.add(block);
                    fields.addAll(textStructureSupport.extractFieldsFromBlock(block));
                } else if (element instanceof XWPFTable t) {
                    String tableContent = extractTableContent(t);
                    int tIdx = tableIndex[0];
                    Block tableBlock = Block.builder()
                            .blockId(UUID.randomUUID().toString())
                            .documentId(docId)
                            .blockType("TABLE")
                            .chapterPath(currentChapter[0])
                            .content(tableContent)
                            .rawContent(tableContent)
                            .anchorChapterPath(currentChapter[0])
                            .anchorParagraphIndex(-1)
                            .anchorTableIndex(tIdx)
                            .anchorTableNo(tIdx + 1)
                            .featureTags(textStructureSupport.detectFieldTags(tableContent))
                            .build();
                    tableBlocks.add(tableBlock);
                    fields.addAll(textStructureSupport.extractFieldsFromBlock(tableBlock));
                    tableIndex[0]++;

                    for (XWPFTableRow row : t.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph cp : cell.getParagraphs()) {
                                Block block = processParagraph(cp, docId, currentChapter, paragraphIndex, sectionTree);
                                paragraphBlocks.add(block);
                                fields.addAll(textStructureSupport.extractFieldsFromBlock(block));
                            }
                        }
                    }
                }
            }
            parseSuccess = true;
        }

        return TenderDocumentParseResult.builder()
                .docId(docId)
                .sectionTree(sectionTree)
                .paragraphBlocks(paragraphBlocks)
                .tableBlocks(tableBlocks)
                .fields(fields)
                .schemaVersion(TenderTextStructureSupport.SCHEMA_VERSION)
                .parserVersion(PARSER_VERSION)
                .parseSuccess(parseSuccess)
                .build();
    }

    private Block processParagraph(XWPFParagraph p, String docId,
                                    String[] currentChapter, int[] paragraphIndex,
                                    List<com.liang.drugagent.scene.tender_review.model.TenderSectionNode> sectionTree) {
        String raw = p.getText();
        String content = textStructureSupport.normalizeText(raw);

        if (textStructureSupport.isSectionHeader(content)) {
            currentChapter[0] = String.valueOf(content.charAt(0));
            sectionTree.add(com.liang.drugagent.scene.tender_review.model.TenderSectionNode.builder()
                    .title(content)
                    .level(1)
                    .children(new ArrayList<>())
                    .build());
        }

        int pIdx = paragraphIndex[0];
        Block block = Block.builder()
                .blockId(UUID.randomUUID().toString())
                .documentId(docId)
                .blockType("PARAGRAPH")
                .chapterPath(currentChapter[0])
                .content(content)
                .rawContent(raw)
                .anchorChapterPath(currentChapter[0])
                .anchorParagraphIndex(pIdx)
                .anchorParagraphNo(pIdx + 1)
                .anchorTableIndex(-1)
                .featureTags(textStructureSupport.detectFieldTags(content))
                .build();

        paragraphIndex[0]++;
        return block;
    }

    private String extractTableContent(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) sb.append('\t');
                sb.append(cells.get(i).getText());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 从 Block 中提取结构化字段，委托给 {@link TenderTextStructureSupport}。
     * 保留此方法以兼容测试代码和外部调用方。
     */
    public java.util.List<com.liang.drugagent.scene.tender_review.model.Field> extractFieldsFromBlock(Block block) {
        return textStructureSupport.extractFieldsFromBlock(block);
    }

}
