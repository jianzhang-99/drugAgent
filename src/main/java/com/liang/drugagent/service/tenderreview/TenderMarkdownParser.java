package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.TenderDocumentParseResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class TenderMarkdownParser {

    private static final String PARSER_VERSION = "markdown-v1.0.0";

    private final TenderTextStructureSupport textStructureSupport;

    public TenderMarkdownParser(TenderTextStructureSupport textStructureSupport) {
        this.textStructureSupport = textStructureSupport;
    }

    public TenderDocumentParseResult parse(InputStream inputStream, String docId) throws IOException {
        String rawText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        List<String> paragraphs = Arrays.stream(rawText.split("\\R\\R+|\\R"))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .toList();
        return textStructureSupport.buildFromParagraphs(paragraphs, docId, PARSER_VERSION);
    }
}
