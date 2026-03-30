package com.liang.drugagent.tool.document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * DOCX 格式解析器。
 *
 * <p>使用 Apache POI 提取文本内容。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocxDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".docx");
    }

    @Override
    public ParsedDocument parse(TempDocument document) {
        if (document.getContent() == null || document.getContent().length == 0) {
            return buildEmpty(document, "文件内容为空");
        }

        try (XWPFDocument docx = new XWPFDocument(
                new ByteArrayInputStream(document.getContent()))) {

            List<XWPFParagraph> paragraphs = docx.getParagraphs();
            StringBuilder plainText = new StringBuilder();

            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    plainText.append(text).append("\n");
                }
            }

            return ParsedDocument.builder()
                    .documentId(document.getDocumentId())
                    .filename(document.getFilename())
                    .fileType("docx")
                    .plainText(plainText.toString())
                    .build();

        } catch (IOException e) {
            log.warn("[DocxDocumentParser] 解析失败: documentId={}, error={}",
                    document.getDocumentId(), e.getMessage());
            return buildEmpty(document, "DOCX解析失败: " + e.getMessage());
        }
    }

    private ParsedDocument buildEmpty(TempDocument document, String error) {
        return ParsedDocument.builder()
                .documentId(document.getDocumentId())
                .filename(document.getFilename())
                .fileType("docx")
                .plainText("")
                .normalizedText("")
                .build();
    }
}
