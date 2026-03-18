package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.TenderDocumentParseResult;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@Component
public class TenderDocParser {

    private static final String PARSER_VERSION = "doc-v1.0.0";

    private final TenderTextStructureSupport textStructureSupport;

    public TenderDocParser(TenderTextStructureSupport textStructureSupport) {
        this.textStructureSupport = textStructureSupport;
    }

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
