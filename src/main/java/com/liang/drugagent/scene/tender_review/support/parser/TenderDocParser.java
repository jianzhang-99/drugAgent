package com.liang.drugagent.scene.tender_review.support.parser;

import com.liang.drugagent.scene.tender_review.model.TenderDocumentParseResult;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/** DOC 格式解析器（老版 .doc）。 */
@Component
public class TenderDocParser {

    private static final String PARSER_VERSION = "doc-v1.0.0";

    private final TenderTextStructureSupport textStructureSupport;

    public TenderDocParser(TenderTextStructureSupport textStructureSupport) {
        this.textStructureSupport = textStructureSupport;
    }

    /**
     * 解析 DOC 文件流。
     *
     * @param inputStream DOC 文件流
     * @param docId       文档 ID
     * @return 解析结果
     * @throws IOException 读取失败时抛出
     */
    public TenderDocumentParseResult parse(InputStream inputStream, String docId) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            List<String> paragraphs = Arrays.stream(extractor.getParagraphText())
                    .map(textStructureSupport::normalizeText)
                    .filter(text -> !text.isBlank())
                    .toList();
            return textStructureSupport.buildFromParagraphs(paragraphs, docId, PARSER_VERSION);
        }
    }
}
