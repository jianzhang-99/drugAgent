package com.liang.drugagent.shared.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档文本提取器。
 *
 * <p>支持从多种格式文档中提取纯文本内容。</p>
 */
@Slf4j
@Component
public class TextExtractor {

    /**
     * 从 MultipartFile 提取文本
     */
    public String extract(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名为空");
        }
        return extract(filename, file.getBytes());
    }

    /**
     * 从文件路径提取文本
     */
    public String extract(Path filePath) throws IOException {
        String filename = filePath.getFileName().toString();
        byte[] content = Files.readAllBytes(filePath);
        return extract(filename, content);
    }

    /**
     * 根据文件扩展名选择提取策略
     */
    private String extract(String filename, byte[] content) throws IOException {
        String lowerName = filename.toLowerCase();

        if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
            return extractText(content);
        }

        if (lowerName.endsWith(".docx")) {
            return extractDocx(content);
        }

        if (lowerName.endsWith(".doc")) {
            log.warn("DOC 格式支持有限，建议转换为 DOCX 格式");
            return extractText(content);
        }

        // 默认为纯文本
        return extractText(content);
    }

    /**
     * 提取纯文本
     */
    private String extractText(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        return cleanText(text);
    }

    /**
     * 提取 DOCX 文本
     * DOCX 本质是 ZIP 文件，包含 document.xml
     */
    private String extractDocx(byte[] content) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(content))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xmlContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return extractTextFromDocxXml(xmlContent);
                }
            }
        }
        throw new IllegalArgumentException("无效的 DOCX 文件结构");
    }

    /**
     * 从 DOCX XML 内容中提取文本
     */
    private String extractTextFromDocxXml(String xmlContent) {
        StringBuilder text = new StringBuilder();
        // 简单解析 XML，提取 w:t 标签内的文本
        String pattern = "<w:t[^>]*>([^<]*)</w:t>";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(xmlContent);
        while (m.find()) {
            if (text.length() > 0) {
                text.append(" ");
            }
            text.append(m.group(1));
        }
        return cleanText(text.toString());
    }

    /**
     * 基础文本清洗
     */
    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        // 移除多余空白字符，保留段落结构
        return text.replaceAll("[ \\t]+", " ")
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
