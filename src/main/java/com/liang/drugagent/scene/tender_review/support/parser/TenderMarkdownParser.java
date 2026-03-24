package com.liang.drugagent.scene.tender_review.support.parser;

import com.liang.drugagent.scene.tender_review.model.TenderDocumentParseResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Markdown 格式解析器。 */
@Component
public class TenderMarkdownParser {

    private static final String PARSER_VERSION = "markdown-v1.0.0";

    private final TenderTextStructureSupport textStructureSupport;

    public TenderMarkdownParser(TenderTextStructureSupport textStructureSupport) {
        this.textStructureSupport = textStructureSupport;
    }

    /**
     * 解析 Markdown 文件流。
     *
     * @param inputStream Markdown 文件流
     * @param docId       文档 ID
     * @return 解析结果
     * @throws IOException 读取失败时抛出
     */
    public TenderDocumentParseResult parse(InputStream inputStream, String docId) throws IOException {
        String rawText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        List<String> paragraphs = Arrays.stream(rawText.split("\\R\\R+|\\R"))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .toList();
        return textStructureSupport.buildFromParagraphs(paragraphs, docId, PARSER_VERSION);
    }
}
