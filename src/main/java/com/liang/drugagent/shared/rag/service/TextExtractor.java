package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.shared.rag.extractor.PdfTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 文档文本提取器。
 *
 * <p>支持从多种格式文档中提取纯文本内容。
 * 支持格式：txt、md、docx、doc、xlsx、xls、pdf。</p>
 */
@Slf4j
@Component
public class TextExtractor {

    private final PdfTextExtractor pdfTextExtractor;

    public TextExtractor(PdfTextExtractor pdfTextExtractor) {
        this.pdfTextExtractor = pdfTextExtractor;
    }

    /**
     * 从 MultipartFile 提取文本
     *
     * @throws TextExtractionException 解析失败时抛出明确异常
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
     *
     * @throws TextExtractionException 解析失败时抛出明确异常
     */
    public String extract(Path filePath) throws IOException {
        String filename = filePath.getFileName().toString();
        byte[] content = Files.readAllBytes(filePath);
        return extract(filename, content);
    }

    /**
     * 根据文件扩展名选择提取策略
     *
     * @throws TextExtractionException 解析失败时抛出明确异常
     */
    private String extract(String filename, byte[] content) throws IOException {
        String lowerName = filename.toLowerCase();

        if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
            return extractText(content);
        }

        if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
            return extractExcel(content, lowerName.endsWith(".xlsx"));
        }

        if (lowerName.endsWith(".docx")) {
            return extractDocx(content);
        }

        if (lowerName.endsWith(".doc")) {
            return extractDoc(content);
        }

        if (lowerName.endsWith(".pdf")) {
            return extractPdf(content);
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
     * 提取 PDF 文本（使用 Apache PDFBox）
     *
     * @throws TextExtractionException PDF 解析失败时抛出
     */
    private String extractPdf(byte[] content) throws IOException {
        try {
            return pdfTextExtractor.extract(content);
        } catch (IOException e) {
            throw new TextExtractionException("PDF 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取 DOCX 文本（使用 Apache POI XWPF）
     *
     * @throws TextExtractionException DOCX 解析失败时抛出
     */
    private String extractDocx(byte[] content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < paragraphs.size(); i++) {
                String paraText = paragraphs.get(i).getText();
                if (paraText != null && !paraText.isBlank()) {
                    if (text.length() > 0) {
                        text.append("\n\n");
                    }
                    text.append(paraText.trim());
                }
            }
            return cleanText(text.toString());
        } catch (Exception e) {
            log.warn("POI 解析 DOCX 失败，降级为 XML 解析: {}", e.getMessage());
            return extractDocxFallback(content);
        }
    }

    /**
     * DOCX XML 降级解析（当 POI 不可用时的兜底方案）
     */
    private String extractDocxFallback(byte[] content) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new ByteArrayInputStream(content))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xmlContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return extractTextFromDocxXml(xmlContent);
                }
                zis.closeEntry();
            }
        }
        throw new TextExtractionException("无效的 DOCX 文件结构");
    }

    /**
     * 从 DOCX XML 内容中提取文本
     */
    private String extractTextFromDocxXml(String xmlContent) {
        StringBuilder text = new StringBuilder();
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
     * 提取 DOC 文本（使用 Apache POI HWPF）
     *
     * @throws TextExtractionException DOC 解析失败时抛出
     */
    private String extractDoc(byte[] content) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(content));
             WordExtractor extractor = new WordExtractor(document)) {
            String[] paragraphs = extractor.getParagraphText();
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < paragraphs.length; i++) {
                String para = paragraphs[i].trim();
                if (!para.isEmpty()) {
                    if (text.length() > 0) {
                        text.append("\n\n");
                    }
                    text.append(para);
                }
            }
            return cleanText(text.toString());
        } catch (Exception e) {
            throw new TextExtractionException("DOC 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取 Excel 文本（使用 Apache POI XSSF/HSSF）
     *
     * @throws TextExtractionException Excel 解析失败时抛出
     */
    private String extractExcel(byte[] content, boolean isXlsx) throws IOException {
        try {
            org.apache.poi.ss.usermodel.Workbook workbook;
            if (isXlsx) {
                workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(content));
            } else {
                workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook(new ByteArrayInputStream(content));
            }
            StringBuilder text = new StringBuilder();
            try {
                for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
                    org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(sheetIdx);
                    if (sheet == null) continue;
                    for (org.apache.poi.ss.usermodel.Row row : sheet) {
                        if (row == null) continue;
                        for (org.apache.poi.ss.usermodel.Cell cell : row) {
                            if (cell == null) continue;
                            String cellText = getCellText(cell);
                            if (cellText != null && !cellText.isBlank()) {
                                text.append(cellText).append("\t");
                            }
                        }
                        if (text.length() > 0 && text.charAt(text.length() - 1) == '\t') {
                            text.append("\n");
                        }
                    }
                }
            } finally {
                workbook.close();
            }
            return cleanText(text.toString());
        } catch (Exception e) {
            throw new TextExtractionException("Excel 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取单元格文本值
     */
    private String getCellText(org.apache.poi.ss.usermodel.Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                // 避免科学计数法
                double val = cell.getNumericCellValue();
                yield (val == Math.floor(val)) ? String.valueOf((long) val) : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield "";
                }
            }
            default -> "";
        };
    }

    /**
     * 基础文本清洗
     */
    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[ \\t]+", " ")
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * 文本提取异常。
     *
     * <p>用于区分"解析失败"与"无有效文本"。
     * 当抛出此异常时，表示文档格式有问题或提取过程中发生错误；
     * 当返回空字符串时，表示文档有效但没有可提取的文本内容。</p>
     */
    public static class TextExtractionException extends IOException {
        public TextExtractionException(String message) {
            super(message);
        }

        public TextExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
