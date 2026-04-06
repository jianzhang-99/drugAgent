package com.liang.drugagent.tool.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * PDF 格式解析器。
 *
 * <p>使用 Apache PDFBox 提取文本内容。</p>
 */
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".pdf");
    }

    @Override
    public ParsedDocument parse(TempDocument document) {
        if (document.getContent() == null || document.getContent().length == 0) {
            return buildEmpty(document, "文件内容为空");
        }

        try (PDDocument pdf = PDDocument.load(
                new ByteArrayInputStream(document.getContent()))) {

            PDFTextStripper stripper = new PDFTextStripper();
            // 按页顺序提取文本，保留文档结构感
            stripper.setSortByPosition(true);
            String plainText = stripper.getText(pdf);

            return ParsedDocument.builder()
                    .documentId(document.getDocumentId())
                    .filename(document.getFilename())
                    .fileType("pdf")
                    .plainText(plainText != null ? plainText : "")
                    .build();

        } catch (IOException e) {
            log.warn("[PdfDocumentParser] 解析失败: documentId={}, error={}",
                    document.getDocumentId(), e.getMessage());
            return buildEmpty(document, "PDF解析失败: " + e.getMessage());
        }
    }

    private ParsedDocument buildEmpty(TempDocument document, String error) {
        return ParsedDocument.builder()
                .documentId(document.getDocumentId())
                .filename(document.getFilename())
                .fileType("pdf")
                .plainText("")
                .normalizedText("")
                .build();
    }
}
