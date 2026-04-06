package com.liang.drugagent.shared.rag.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * PDF 文档文本提取器。
 *
 * <p>使用 Apache PDFBox 3.x 从 PDF 文件中提取纯文本内容。
 * 支持文本顺序读取、基础清洗、页码追踪。</p>
 */
@Slf4j
@Component
public class PdfTextExtractor {

    /**
     * 从 PDF 文件字节数组提取文本
     *
     * @param content PDF 文件字节内容
     * @return 提取的纯文本内容
     * @throws IOException PDF 解析失败时抛出
     */
    public String extract(byte[] content) throws IOException {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("PDF 内容不能为空");
        }

        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return cleanText(text);
        } catch (IOException e) {
            log.error("PDF 解析失败", e);
            throw new IOException("PDF 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 PDF 文件字节数组提取文本，可选页码范围
     *
     * @param content PDF 文件字节内容
     * @param startPage 起始页码（从1开始），null表示从头开始
     * @param endPage 结束页码，null表示到末尾
     * @return 提取的纯文本内容
     * @throws IOException PDF 解析失败时抛出
     */
    public String extract(byte[] content, Integer startPage, Integer endPage) throws IOException {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("PDF 内容不能为空");
        }

        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();

            if (startPage != null && startPage > 0) {
                stripper.setStartPage(startPage);
            }
            if (endPage != null && endPage > 0) {
                stripper.setEndPage(endPage);
            }

            String text = stripper.getText(document);
            return cleanText(text);
        } catch (IOException e) {
            log.error("PDF 解析失败", e);
            throw new IOException("PDF 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取所有页面的文本，并标注页码
     *
     * @param content PDF 文件字节内容
     * @return 带页码标注的文本内容，格式为 "=== 第N页 ===\n页面内容\n"
     * @throws IOException PDF 解析失败时抛出
     */
    public String extractWithPageNumbers(byte[] content) throws IOException {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("PDF 内容不能为空");
        }

        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder result = new StringBuilder();
            int totalPages = document.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                if (pageText != null && !pageText.isBlank()) {
                    result.append("=== 第").append(i).append("页 ===\n");
                    result.append(pageText.trim());
                    result.append("\n\n");
                }
            }

            return cleanText(result.toString());
        } catch (IOException e) {
            log.error("PDF 解析失败（带页码）", e);
            throw new IOException("PDF 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 基础文本清洗
     */
    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        // 移除多余空白但保留段落结构
        return text.replaceAll("[ \\t]+", " ")
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
