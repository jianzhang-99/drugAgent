package com.liang.drugagent.tool.document.parser;

import com.liang.drugagent.tool.document.model.DocumentBlock;
import com.liang.drugagent.tool.document.model.DocumentParseResult;
import com.liang.drugagent.tool.document.model.DocumentSectionNode;
import com.liang.drugagent.tool.document.support.DocumentTextNormalizer;
import com.liang.drugagent.tool.document.support.DocumentSectionRecognizer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Markdown 格式文档解析器。
 *
 * @author liangjiajian
 */
@Component
public class MarkdownDocumentParser implements DocumentParser {

    public static final String PARSER_VERSION = "markdown-v1.0.0";

    private final DocumentTextNormalizer textNormalizer;
    private final DocumentSectionRecognizer sectionRecognizer;

    public MarkdownDocumentParser(DocumentTextNormalizer textNormalizer,
                                  DocumentSectionRecognizer sectionRecognizer) {
        this.textNormalizer = textNormalizer;
        this.sectionRecognizer = sectionRecognizer;
    }

    @Override
    public boolean supports(String fileType) {
        return "md".equalsIgnoreCase(fileType);
    }

    @Override
    public DocumentParseResult parse(InputStream inputStream, String docId) throws IOException {
        List<DocumentBlock> paragraphBlocks = new ArrayList<>();
        List<DocumentBlock> tableBlocks = new ArrayList<>();
        List<DocumentSectionNode> sectionTree = new ArrayList<>();
        String[] currentChapter = {""};

        String rawText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        List<String> paragraphs = Arrays.stream(rawText.split("\\R\\R+|\\R"))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .toList();

        int idx = 0;
        for (String para : paragraphs) {
            String content = textNormalizer.normalize(para);

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
                    .rawContent(para)
                    .anchorChapterPath(currentChapter[0])
                    .anchorParagraphIndex(idx)
                    .anchorParagraphNo(idx + 1)
                    .anchorTableIndex(-1)
                    .build();
            paragraphBlocks.add(block);
            idx++;
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
