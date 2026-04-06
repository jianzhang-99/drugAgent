package com.liang.drugagent.tool.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Markdown / 纯文本解析器。
 *
 * <p>Markdown 与 TXT 均为 UTF-8 可读字节流，直接解码即可。
 * 常见测试数据命名为 {@code *.pdf.txt}，扩展名实为 {@code .txt}，此前无解析器认领会导致整批标书解析失败。</p>
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
        return lower.endsWith(".md")
                || lower.endsWith(".markdown")
                || lower.endsWith(".txt")
                || lower.endsWith(".text");
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
                .fileType(resolveFileType(document.getFilename()))
                .plainText(plainText)
                .build();
    }

    private ParsedDocument buildEmpty(TempDocument document, String error) {
        return ParsedDocument.builder()
                .documentId(document.getDocumentId())
                .filename(document.getFilename())
                .fileType(resolveFileType(document.getFilename()))
                .plainText("")
                .normalizedText("")
                .build();
    }

    private static String resolveFileType(String filename) {
        if (filename == null) {
            return "txt";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "md";
        }
        return "txt";
    }
}
