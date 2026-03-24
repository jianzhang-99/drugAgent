package com.liang.drugagent.tool.document.parser;

import com.liang.drugagent.tool.document.model.DocumentBlock;
import com.liang.drugagent.tool.document.model.DocumentParseResult;
import com.liang.drugagent.tool.document.model.DocumentSectionNode;
import com.liang.drugagent.tool.document.support.DocumentTextNormalizer;
import com.liang.drugagent.tool.document.support.DocumentSectionRecognizer;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * DOC 格式文档解析器（老版 .doc）。
 *
 * @author liangjiajian
 */
@Component
public class DocDocumentParser implements DocumentParser {

    public static final String PARSER_VERSION = "doc-v1.0.0";

    private final DocumentTextNormalizer textNormalizer;
    private final DocumentSectionRecognizer sectionRecognizer;

    public DocDocumentParser(DocumentTextNormalizer textNormalizer,
                             DocumentSectionRecognizer sectionRecognizer) {
        this.textNormalizer = textNormalizer;
        this.sectionRecognizer = sectionRecognizer;
    }

    @Override
    public boolean supports(String fileType) {
        return "doc".equalsIgnoreCase(fileType);
    }

    @Override
    public DocumentParseResult parse(InputStream inputStream, String docId) throws IOException {
        List<DocumentBlock> paragraphBlocks = new ArrayList<>();
        List<DocumentBlock> tableBlocks = new ArrayList<>();
        List<DocumentSectionNode> sectionTree = new ArrayList<>();
        String[] currentChapter = {""};

        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            String[] paragraphIndex = {""};
            int idx = 0;

            for (String paraText : extractor.getParagraphText()) {
                String raw = paraText;
                String content = textNormalizer.normalize(raw);
                if (content.isBlank()) continue;

                if (sectionRecognizer.isSectionHeader(content)) {
                    currentChapter[0] = String.valueOf(content.charAt(0));
                    sectionTree.add(DocumentSectionNode.builder()
                            .title(content)
                            .level(1)
                            .children(new ArrayList<>())
                            .build());
                }

                DocumentBlock block = DocumentBlock.builder()
                        .blockId(UUID.randomUUID().toString())
                        .documentId(docId)
                        .blockType("PARAGRAPH")
                        .chapterPath(currentChapter[0])
                        .content(content)
                        .rawContent(raw)
                        .anchorChapterPath(currentChapter[0])
                        .anchorParagraphIndex(idx)
                        .anchorParagraphNo(idx + 1)
                        .anchorTableIndex(-1)
                        .build();
                paragraphBlocks.add(block);
                idx++;
            }
        }

        return DocumentParseResult.builder()
                .docId(docId)
                .sectionTree(sectionTree)
                .paragraphBlocks(paragraphBlocks)
                .tableBlocks(tableBlocks)
                .schemaVersion(DocxDocumentParser.SCHEMA_VERSION)
                .parserVersion(PARSER_VERSION)
                .parseSuccess(true)
                .build();
    }
}
