package com.liang.drugagent.tool.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Markdown 格式解析器。
 *
 * <p>Markdown 为纯文本格式，直接读取内容即可。</p>
 */
@Slf4j
@Component
public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    @Override
    public ParsedDocument parse(TempDocument document) {
        if (document.getContent() == null || document.getContent().length == 0) {
            return buildEmpty(document, "文件内容为空");
        }

        String plainText;
        try {
            plainText = new String(document.getContent(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[MarkdownDocumentParser] 解析失败: documentId={}, error={}",
                    document.getDocumentId(), e.getMessage());
            plainText = "";
        }

        return ParsedDocument.builder()
                .documentId(document.getDocumentId())
                .filename(document.getFilename())
                .fileType("md")
                .plainText(plainText)
                .build();
    }

    private ParsedDocument buildEmpty(TempDocument document, String error) {
        return ParsedDocument.builder()
                .documentId(document.getDocumentId())
                .filename(document.getFilename())
                .fileType("md")
                .plainText("")
                .normalizedText("")
                .build();
    }
}
