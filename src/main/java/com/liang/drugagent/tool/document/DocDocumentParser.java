package com.liang.drugagent.tool.document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * DOC 格式解析器（老版 Word 格式）。
 *
 * <p>使用 Apache POI Scratchpad 提取文本内容。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".doc") && !lower.endsWith(".docx");
    }

    @Override
    public ParsedDocument parse(TempDocument document) {
        if (document.getContent() == null || document.getContent().length == 0) {
            return buildEmpty(document, "文件内容为空");
        }

        try (HWPFDocument doc = new HWPFDocument(
                new ByteArrayInputStream(document.getContent()));
             WordExtractor extractor = new WordExtractor(doc)) {

            String plainText = extractor.getText();

            return ParsedDocument.builder()
                    .documentId(document.getDocumentId())
                    .filename(document.getFilename())
                    .fileType("doc")
                    .plainText(plainText != null ? plainText : "")
                    .build();

        } catch (IOException e) {
            log.warn("[DocDocumentParser] 解析失败: documentId={}, error={}",
                    document.getDocumentId(), e.getMessage());
            return buildEmpty(document, "DOC解析失败: " + e.getMessage());
        }
    }

    private ParsedDocument buildEmpty(TempDocument document, String error) {
        return ParsedDocument.builder()
                .documentId(document.getDocumentId())
                .filename(document.getFilename())
                .fileType("doc")
                .plainText("")
                .normalizedText("")
                .build();
    }
}
