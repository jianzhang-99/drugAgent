package com.liang.drugagent.tool.document.parser;

import com.liang.drugagent.tool.document.model.DocumentBlock;
import com.liang.drugagent.tool.document.model.DocumentParseResult;
import com.liang.drugagent.tool.document.model.DocumentSectionNode;
import com.liang.drugagent.tool.document.support.DocumentTextNormalizer;
import com.liang.drugagent.tool.document.support.DocumentSectionRecognizer;
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

/**
 * DOCX 格式文档解析器。
 *
 * @author liangjiajian
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    public static final String SCHEMA_VERSION = "document-parse-v1";
    public static final String PARSER_VERSION = "docx-v1.0.0";

    private final DocumentTextNormalizer textNormalizer;
    private final DocumentSectionRecognizer sectionRecognizer;

    public DocxDocumentParser(DocumentTextNormalizer textNormalizer,
                             DocumentSectionRecognizer sectionRecognizer) {
        this.textNormalizer = textNormalizer;
        this.sectionRecognizer = sectionRecognizer;
    }

    @Override
    public boolean supports(String fileType) {
        return "docx".equalsIgnoreCase(fileType);
    }

    @Override
    public DocumentParseResult parse(InputStream inputStream, String docId) throws IOException {
        boolean parseSuccess = false;
        List<DocumentBlock> paragraphBlocks = new ArrayList<>();
        List<DocumentBlock> tableBlocks = new ArrayList<>();
        List<DocumentSectionNode> sectionTree = new ArrayList<>();

        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            String[] currentChapter = {""};
            int[] paragraphIndex = {0};
            int[] tableIndex = {0};

            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    DocumentBlock block = processParagraph(p, docId, currentChapter, paragraphIndex, sectionTree);
                    paragraphBlocks.add(block);
                } else if (element instanceof XWPFTable t) {
                    DocumentBlock tableBlock = processTable(t, docId, currentChapter, tableIndex, sectionTree);
                    tableBlocks.add(tableBlock);
                }
            }
            parseSuccess = true;
        }

        return DocumentParseResult.builder()
                .docId(docId)
                .sectionTree(sectionTree)
                .paragraphBlocks(paragraphBlocks)
                .tableBlocks(tableBlocks)
                .schemaVersion(SCHEMA_VERSION)
                .parserVersion(PARSER_VERSION)
                .parseSuccess(parseSuccess)
                .build();
    }

    private DocumentBlock processParagraph(XWPFParagraph p, String docId,
                                          String[] currentChapter, int[] paragraphIndex,
                                          List<DocumentSectionNode> sectionTree) {
        String raw = p.getText();
        String content = textNormalizer.normalize(raw);

        if (sectionRecognizer.isSectionHeader(content)) {
            currentChapter[0] = String.valueOf(content.charAt(0));
            sectionTree.add(DocumentSectionNode.builder()
                    .title(content)
                    .level(1)
                    .children(new ArrayList<>())
                    .build());
        }

        int pIdx = paragraphIndex[0];
        DocumentBlock block = DocumentBlock.builder()
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
                .build();

        paragraphIndex[0]++;
        return block;
    }

    private DocumentBlock processTable(XWPFTable table, String docId,
                                        String[] currentChapter, int[] tableIndex,
                                        List<DocumentSectionNode> sectionTree) {
        String tableContent = extractTableContent(table);
        int tIdx = tableIndex[0];

        DocumentBlock block = DocumentBlock.builder()
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
                .build();

        tableIndex[0]++;
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
}
